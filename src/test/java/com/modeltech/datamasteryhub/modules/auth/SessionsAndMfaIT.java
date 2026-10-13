package com.modeltech.datamasteryhub.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.AuthSession;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.AuthSessionRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.auth.security.Base32;
import com.modeltech.datamasteryhub.modules.auth.security.Totp;
import com.modeltech.datamasteryhub.modules.auth.service.AuthSessionService;
import com.modeltech.datamasteryhub.modules.auth.service.LoginThrottle;
import com.modeltech.datamasteryhub.modules.auth.service.MfaPolicy;
import com.modeltech.datamasteryhub.modules.auth.service.impl.UserDetailsServiceImpl;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import com.modeltech.datamasteryhub.security.AppUserDetails;
import com.modeltech.datamasteryhub.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions (jeton d'accès de 15 minutes + cookie de rafraîchissement HttpOnly) et double authentification obligatoire
 * du personnel : enrôlement, vérification, codes de secours, rotation et rejeu, invalidation par événement de sécurité.
 */
class SessionsAndMfaIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Cormoran-Biscuit-2026";
    private static final String ORIGIN = "http://localhost:5173";
    private static final String COOKIE = "mt_refresh";

    @MockBean private MessageDispatcher dispatcher;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MfaPolicy mfaPolicy;
    @Autowired private LoginThrottle loginThrottle;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private AuthSessionRepository sessionRepository;
    @Autowired private AuthSessionService sessionService;
    @Autowired private UserDetailsServiceImpl userDetailsService;

    private AdminUser superAdmin;
    private AdminUser staff;
    private Learner awa;

    @Autowired private org.springframework.web.context.WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        // Les tests partagent un seul fil : une authentification posée par le filtre JWT lors d'une requête ne doit pas
        // « fuiter » vers la suivante (en production, SecurityContextHolderFilter la vide à la fin de chaque requête).
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").with(request -> {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
                    return request;
                }))
                .build();
        reset(dispatcher, notificationService);
        ReflectionTestUtils.setField(mfaPolicy, "enforced", true);
        for (String email : List.of("super@example.com", "staff@example.com", "awa@example.com")) loginThrottle.reset(email, "127.0.0.1");

        superAdmin = staffAccount("super@example.com", "ROLE_SUPER_ADMIN");
        staff = staffAccount("staff@example.com", "ROLE_ADMIN");
        awa = new Learner();
        awa.setEmail("awa@example.com");
        awa.setFirstName("Awa");
        awa.setLastName("Diop");
        awa.setPasswordHash(passwordEncoder.encode(PASSWORD));
        awa.setRoles(new HashSet<>(List.of(roleRepository.findByName("ROLE_LEARNER").orElseThrow())));
        awa = learnerRepository.save(awa);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(mfaPolicy, "enforced", false);
    }

    // ── Enrôlement et vérification ───────────────────────────────────

    @Test
    void staffLogin_firstRequiresEnrollment_thenOpensASessionWithAnHttpOnlyCookie_andRecoveryCodes() throws Exception {
        MvcResult login = login("staff@example.com", PASSWORD);
        JsonNode body = json(login);

        assertThat(body.path("mfaEnrollmentRequired").asBoolean()).isTrue();
        assertThat(body.has("accessToken")).isFalse();                 // aucun jeton d'accès avant le second facteur
        assertThat(login.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(login.getResponse().getHeader("Cache-Control")).contains("no-store");
        String challenge = body.get("mfaToken").asText();

        JsonNode enroll = json(mockMvc.perform(post("/api/v1/auth/mfa/enroll/start").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + challenge + "\"}")).andExpect(status().isOk()).andReturn());
        assertThat(enroll.get("secret").asText()).matches("[A-Z2-7]{32}");
        assertThat(enroll.get("otpauthUri").asText()).startsWith("otpauth://totp/Model%20Technologie:staff%40example.com?secret=");
        assertThat(adminUserRepository.findById(staff.getId()).orElseThrow().isMfaEnabled()).isFalse();   // pas actif avant confirmation
        assertThat(adminUserRepository.findById(staff.getId()).orElseThrow().getMfaSecretEnc()).doesNotContain(enroll.get("secret").asText());

        // Un mauvais code n'active rien
        send("/api/v1/auth/mfa/enroll/confirm", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"000000\"}").andExpect(status().isUnauthorized());

        MvcResult confirmed = send("/api/v1/auth/mfa/enroll/confirm", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + code(enroll.get("secret").asText(), 0) + "\"}").andExpect(status().isOk()).andReturn();
        JsonNode session = json(confirmed);
        assertThat(session.get("accessToken").asText()).isNotBlank();
        assertThat(session.get("expiresIn").asLong()).isEqualTo(900_000);
        assertThat(session.get("recoveryCodes")).hasSize(10);
        assertThat(session.get("recoveryCodes").get(0).asText()).matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
        assertThat(session.get("user").get("mfaEnabled").asBoolean()).isTrue();

        String setCookie = confirmed.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly").contains("Secure").contains("SameSite=Strict").contains("Path=/api/v1/auth");
        assertThat(adminUserRepository.findById(staff.getId()).orElseThrow().isMfaEnabled()).isTrue();

        // Le jeton d'accès ouvre les routes protégées
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("staff@example.com"));
    }

    @Test
    void anEnrolledAccount_needsTheCodeAtEveryLogin_aUsedCodeCannotBeReplayed_andAWrongOneIsRefused() throws Exception {
        String secret = enroll(staff);
        String challenge = json(login("staff@example.com", PASSWORD)).get("mfaToken").asText();
        assertThat(json(login("staff@example.com", PASSWORD)).path("mfaRequired").asBoolean()).isTrue();

        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"000000\"}").andExpect(status().isUnauthorized());
        // le code qui a servi à l'enrôlement ne sert pas une seconde fois
        long used = lastStepOf(staff);
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + codeAtStep(secret, used) + "\"}").andExpect(status().isUnauthorized());
        // le code du pas suivant est accepté
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + codeAtStep(secret, used + 1) + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aRecoveryCodeWorksOnce_andTheRemainingCountIsReported() throws Exception {
        enroll(staff);
        List<String> codes = recoveryCodesOf(staff);
        String challenge = json(login("staff@example.com", PASSWORD)).get("mfaToken").asText();

        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + codes.get(0).toLowerCase() + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodesRemaining").value(9));
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + codes.get(0) + "\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongCodesLockTheVerification() throws Exception {
        String secret = enroll(staff);
        String challenge = json(login("staff@example.com", PASSWORD)).get("mfaToken").asText();
        for (int i = 0; i < 5; i++) {
            send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"11111" + i + "\"}").andExpect(status().isUnauthorized());
        }
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + codeAtStep(secret, lastStepOf(staff) + 1) + "\"}").andExpect(status().isTooManyRequests());
    }

    @Test
    void theChallengeIsTiedToTheAccountState_andToItsPurpose() throws Exception {
        enroll(staff);
        String enrolledChallenge = json(login("staff@example.com", PASSWORD)).get("mfaToken").asText();

        // un défi « vérification » ne permet pas de recommencer l'enrôlement
        send("/api/v1/auth/mfa/enroll/start", "{\"mfaToken\":\"" + enrolledChallenge + "\"}").andExpect(status().isConflict());
        // un jeton quelconque n'est pas un défi
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"n-importe-quoi\",\"code\":\"123456\"}").andExpect(status().isUnauthorized());
        // un jeton d'accès n'est pas un défi
        String access = accessTokenWithoutSecondFactor(staff);
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + access + "\",\"code\":\"123456\"}").andExpect(status().isUnauthorized());

        // un défi devient caduc si le compte change de version de sécurité (mot de passe, rôle, réinitialisation…)
        AdminUser reloaded = adminUserRepository.findById(staff.getId()).orElseThrow();
        reloaded.setSecurityVersion(reloaded.getSecurityVersion() + 1);
        adminUserRepository.saveAndFlush(reloaded);
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + enrolledChallenge + "\",\"code\":\"123456\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void anAccessTokenIssuedWithoutTheSecondFactor_andAChallenge_areRefusedOnProtectedRoutes() throws Exception {
        enroll(staff);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessTokenWithoutSecondFactor(staff)))
                .andExpect(status().isUnauthorized());

        String challenge = json(login("staff@example.com", PASSWORD)).get("mfaToken").asText();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + challenge)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + challenge)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordlessLoginAndGoogle_doNotSkipTheSecondFactorForStaff() throws Exception {
        enroll(staff);
        mockMvc.perform(post("/api/v1/auth/passwordless/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"staff@example.com\"}")).andExpect(status().isOk());
        ArgumentCaptor<List<OutboundMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).sendAll(messages.capture(), any());
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(messages.getValue().get(0).body());
        assertThat(m.find()).isTrue();

        mockMvc.perform(post("/api/v1/auth/passwordless/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + m.group(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    // ── Rafraîchissement ─────────────────────────────────────────────

    @Test
    void refresh_rotatesTheCookie_andIssuesANewAccessToken() throws Exception {
        MvcResult session = openSession(staff);
        String first = cookieValue(session);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("staff@example.com")).andReturn();
        String second = cookieValue(refreshed);
        assertThat(second).isNotEqualTo(first);

        // le nouveau cookie fonctionne
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, second))).andExpect(status().isOk());
    }

    @Test
    void aReplayedRefreshToken_revokesTheSession_afterTheGraceDelay() throws Exception {
        ReflectionTestUtils.setField(sessionService, "graceSeconds", 0);
        try {
            String first = cookieValue(openSession(staff));
            String second = cookieValue(mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, first)))
                    .andExpect(status().isOk()).andReturn());

            // l'ancien jeton est rejoué (vol probable) : refusé, et la session entière est révoquée
            mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, first))).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, second))).andExpect(status().isUnauthorized());
        } finally {
            ReflectionTestUtils.setField(sessionService, "graceSeconds", 10);
        }
    }

    @Test
    void twoTabsRefreshingAtOnce_areTolerated_withoutAnotherCookie() throws Exception {
        String first = cookieValue(openSession(staff));
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, first))).andExpect(status().isOk());

        MvcResult concurrent = mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();
        assertThat(concurrent.getResponse().getHeader("Set-Cookie")).isNull();   // le cookie du premier onglet reste le bon
    }

    @Test
    void refresh_requiresATrustedOrigin_andACookie() throws Exception {
        String cookie = cookieValue(openSession(staff));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, cookie))).andExpect(status().isForbidden());   // sans Origin
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://evil.example").cookie(new Cookie(COOKIE, cookie))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN)).andExpect(status().isUnauthorized());              // sans cookie
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, "invente"))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout").header("Origin", "https://evil.example").cookie(new Cookie(COOKIE, cookie))).andExpect(status().isForbidden());
    }

    @Test
    void anExpiredSession_cannotBeRefreshed_neitherIdleNorPastItsAbsoluteLimit() throws Exception {
        String cookie = cookieValue(openSession(staff));
        AuthSession stored = sessionRepository.findAll().get(0);

        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        sessionRepository.saveAndFlush(stored);
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isUnauthorized());

        stored.setExpiresAt(LocalDateTime.now().plusHours(1));
        stored.setMaxExpiresAt(LocalDateTime.now().minusMinutes(1));
        sessionRepository.saveAndFlush(stored);
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionsAreStoredOnlyAsAHash_withTheirOrigin() throws Exception {
        String cookie = cookieValue(openSession(staff));
        AuthSession stored = sessionRepository.findAll().get(0);

        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(cookie);
        assertThat(stored.getSubjectType()).isEqualTo("ADMIN");
        assertThat(stored.isMfaVerified()).isTrue();
        assertThat(stored.getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void aSessionAndItsTokens_doNotSurviveTheDisappearanceOfTheAccountsSecondFactor() throws Exception {
        MvcResult session = openSession(staff);
        String access = json(session).get("accessToken").asText();
        String cookie = cookieValue(session);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());

        // Le second facteur disparaît sans passer par la réinitialisation officielle (intervention directe en base)
        AdminUser stored = adminUserRepository.findById(staff.getId()).orElseThrow();
        stored.setMfaSecretEnc(null);
        stored.setMfaEnabledAt(null);
        adminUserRepository.saveAndFlush(stored);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isUnauthorized());
    }

    // ── Déconnexion et événements de sécurité ────────────────────────

    @Test
    void logout_endsTheSession_clearsTheCookie_andRevokesTheAccessToken() throws Exception {
        MvcResult session = openSession(staff);
        String access = json(session).get("accessToken").asText();
        String cookie = cookieValue(session);

        MvcResult out = mockMvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN)
                .header("Authorization", "Bearer " + access).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isOk()).andReturn();
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains(COOKIE + "=").contains("Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_worksEvenWithAnExpiredAccessToken_becauseTheCookieEndsTheSession() throws Exception {
        String cookie = cookieValue(openSession(staff));
        mockMvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isUnauthorized());
    }

    @Test
    void changingThePassword_endsEverySessionAndInvalidatesIssuedTokens() throws Exception {
        MvcResult session = openSession(staff);
        String access = json(session).get("accessToken").asText();
        String cookie = cookieValue(session);

        mockMvc.perform(put("/api/v1/auth/change-password").header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"court\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/auth/change-password").header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"MotDePasse#2026\"}")).andExpect(status().isBadRequest());   // mot de passe courant refusé
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());      // les refus n'ont rien invalidé

        mockMvc.perform(put("/api/v1/auth/change-password").header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Pelican-Violet-Horizon-84\"}")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookie))).andExpect(status().isUnauthorized());
        assertThat(json(login("staff@example.com", "Pelican-Violet-Horizon-84")).path("mfaRequired").asBoolean()).isTrue();
    }

    @Test
    void aRoleChange_orADeactivation_invalidatesTokensAlreadyIssued() throws Exception {
        String superToken = json(openSession(superAdmin)).get("accessToken").asText();
        MvcResult staffSession = openSession(staff);
        String staffToken = json(staffSession).get("accessToken").asText();
        String staffCookie = cookieValue(staffSession);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + staffToken)).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/users/" + staff.getId()).header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"EDITOR\"]}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + staffToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, staffCookie))).andExpect(status().isUnauthorized());
    }

    // ── Réinitialisation du MFA par un SUPER_ADMIN ───────────────────

    @Test
    void aSuperAdminCanResetSomeoneElsesMfa_whichEndsTheirSessions_notifiesThem_andForcesANewEnrollment() throws Exception {
        enroll(staff);
        String superToken = json(openSession(superAdmin)).get("accessToken").asText();
        MvcResult staffSession = openSession(staff);
        String staffToken = json(staffSession).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/admin/users/" + staff.getId() + "/mfa/reset").header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mfaEnabled").value(false));

        verify(notificationService).sendMfaResetEmail(eq("staff@example.com"), anyString());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + staffToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookieValue(staffSession)))).andExpect(status().isUnauthorized());
        assertThat(json(login("staff@example.com", PASSWORD)).path("mfaEnrollmentRequired").asBoolean()).isTrue();
        assertThat(adminUserRepository.findById(staff.getId()).orElseThrow().getMfaSecretEnc()).isNull();
    }

    @Test
    void nobodyResetsTheirOwnMfa_andOnlySuperAdminsMay() throws Exception {
        String superToken = json(openSession(superAdmin)).get("accessToken").asText();
        mockMvc.perform(post("/api/v1/admin/users/" + superAdmin.getId() + "/mfa/reset").header("Authorization", "Bearer " + superToken))
                .andExpect(status().isConflict());

        String staffToken = json(openSession(staff)).get("accessToken").asText();
        mockMvc.perform(post("/api/v1/admin/users/" + superAdmin.getId() + "/mfa/reset").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void recoveryCodes_canBeRegenerated_withAValidCode_andTheOldOnesStopWorking() throws Exception {
        String secret = enroll(staff);
        List<String> before = recoveryCodesOf(staff);
        String access = json(openSession(staff)).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/mfa/recovery-codes").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}")).andExpect(status().isUnauthorized());
        MvcResult regenerated = mockMvc.perform(post("/api/v1/auth/mfa/recovery-codes").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + before.get(1) + "\"}")).andExpect(status().isOk()).andReturn();
        JsonNode fresh = json(regenerated).get("recoveryCodes");
        assertThat(fresh).hasSize(10);

        String challenge = json(login("staff@example.com", PASSWORD)).get("mfaToken").asText();
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + before.get(2) + "\"}").andExpect(status().isUnauthorized());
        send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + fresh.get(0).asText() + "\"}").andExpect(status().isOk());
        assertThat(secret).isNotBlank();
    }

    // ── Apprenants : parcours inchangé ───────────────────────────────

    @Test
    void learners_stillLogInWithoutASecondFactor_withTheSameCookieSession() throws Exception {
        MvcResult login = login("awa@example.com", PASSWORD);
        JsonNode body = json(login);

        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.has("mfaRequired")).isFalse();
        assertThat(body.get("user").get("userType").asText()).isEqualTo("LEARNER");
        assertThat(login.getResponse().getHeader("Set-Cookie")).contains(COOKIE + "=").contains("HttpOnly");

        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookieValue(login))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.userType").value("LEARNER"));
    }

    @Test
    void aDeactivatedLearner_losesTheSessionAtOnce() throws Exception {
        MvcResult login = login("awa@example.com", PASSWORD);
        String access = json(login).get("accessToken").asText();
        String superToken = json(openSession(superAdmin)).get("accessToken").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/learners/" + awa.getId() + "/deactivate")
                .header("Authorization", "Bearer " + superToken)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, cookieValue(login)))).andExpect(status().isUnauthorized());
    }

    // ── Mots de passe ────────────────────────────────────────────────

    @Test
    void aWeakPasswordIsRefusedAtReset_andTheLinkStaysUsable() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"staff@example.com\"}")).andExpect(status().isOk());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyPasswordResetEmail(eq("staff@example.com"), link.capture(), org.mockito.ArgumentMatchers.anyInt());
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"Password123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("trop courant")));
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"Pelican-Violet-Horizon-84\"}"))
                .andExpect(status().isOk());
    }

    // ── Outils ───────────────────────────────────────────────────────

    private AdminUser staffAccount(String email, String role) {
        AdminUser account = new AdminUser();
        account.setEmail(email);
        account.setFullName("Compte " + email);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setRoles(new HashSet<>(List.of(roleRepository.findByName(role).orElseThrow())));
        return adminUserRepository.save(account);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions send(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Valeur du cookie de session posé par la réponse. */
    private String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).as("cookie de session").isNotNull().startsWith(COOKIE + "=");
        return header.substring((COOKIE + "=").length(), header.indexOf(';'));
    }

    /** Code attendu par l'application d'authentification, {@code stepOffset} pas après l'instant présent. */
    private String code(String base32Secret, int stepOffset) {
        return Totp.code(Base32.decode(base32Secret), Totp.stepAt(Instant.now().getEpochSecond()) + stepOffset);
    }

    private long lastStepOf(AdminUser account) {
        return adminUserRepository.findById(account.getId()).orElseThrow().getMfaLastStep();
    }

    private String codeAtStep(String base32Secret, long step) {
        return Totp.code(Base32.decode(base32Secret), step);
    }

    private String lastSecret;

    /** Enrôle le compte à la manière d'un utilisateur (connexion, démarrage, confirmation) et renvoie son secret. */
    private String enroll(AdminUser account) throws Exception {
        String challenge = json(login(account.getEmail(), PASSWORD)).get("mfaToken").asText();
        String secret = json(send("/api/v1/auth/mfa/enroll/start", "{\"mfaToken\":\"" + challenge + "\"}").andExpect(status().isOk()).andReturn()).get("secret").asText();
        MvcResult confirmed = send("/api/v1/auth/mfa/enroll/confirm", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + code(secret, 0) + "\"}").andExpect(status().isOk()).andReturn();
        recoveryCodes = new java.util.ArrayList<>();
        json(confirmed).get("recoveryCodes").forEach(c -> recoveryCodes.add(c.asText()));
        lastSecret = secret;
        return secret;
    }

    private List<String> recoveryCodes;

    private List<String> recoveryCodesOf(AdminUser account) {
        return recoveryCodes;
    }

    /** Ouvre une session complète (compte enrôlé si besoin, premier facteur + code) et renvoie la réponse de la dernière étape. */
    private MvcResult openSession(AdminUser account) throws Exception {
        AdminUser stored = adminUserRepository.findById(account.getId()).orElseThrow();
        if (!stored.isMfaEnabled()) {
            String challenge = json(login(account.getEmail(), PASSWORD)).get("mfaToken").asText();
            String secret = json(send("/api/v1/auth/mfa/enroll/start", "{\"mfaToken\":\"" + challenge + "\"}").andReturn()).get("secret").asText();
            MvcResult confirmed = send("/api/v1/auth/mfa/enroll/confirm", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + code(secret, 0) + "\"}").andExpect(status().isOk()).andReturn();
            stored = adminUserRepository.findById(account.getId()).orElseThrow();
            secrets.put(account.getId(), secret);
            return confirmed;
        }
        String challenge = json(login(account.getEmail(), PASSWORD)).get("mfaToken").asText();
        // Le dernier pas accepté est remis à zéro : un test ouvre plusieurs sessions dans la même fenêtre de 30 secondes
        stored.setMfaLastStep(null);
        adminUserRepository.saveAndFlush(stored);
        String secret = secrets.getOrDefault(account.getId(), lastSecret);
        return send("/api/v1/auth/mfa/verify", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + code(secret, 0) + "\"}")
                .andExpect(status().isOk()).andReturn();
    }

    private final java.util.Map<UUID, String> secrets = new java.util.HashMap<>();

    /** Jeton d'accès valide en tout point, sauf que le second facteur n'a pas été validé. */
    private String accessTokenWithoutSecondFactor(AdminUser account) {
        AppUserDetails details = (AppUserDetails) userDetailsService.loadUserByUsername(account.getEmail());
        return jwt.generateAccessToken(details, UUID.randomUUID(), false);
    }
}
