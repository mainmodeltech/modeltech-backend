package com.modeltech.datamasteryhub.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.LoginChallenge;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LoginChallengeRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.auth.service.GoogleIdTokenVerifier;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connexion par lien/code, par Google, et verrouillage après échecs de mot de passe. */
class AdvancedAuthIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "MotDePasse#2026";

    @MockBean private MessageDispatcher dispatcher;
    @MockBean private GoogleIdTokenVerifier googleVerifier;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private LoginChallengeRepository challengeRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Learner awa;
    private AdminUser staff;

    @BeforeEach
    void setUp() {
        reset(dispatcher, googleVerifier);
        awa = new Learner();
        awa.setEmail("awa@example.com");
        awa.setFirstName("Awa");
        awa.setLastName("Diop");
        awa.setPhone("771234567");
        awa.setRoles(new HashSet<>(List.of(roleRepository.findByName("ROLE_LEARNER").orElseThrow())));
        awa = learnerRepository.save(awa);   // pas de mot de passe : compte invité

        staff = new AdminUser();
        staff.setEmail("staff@example.com");
        staff.setFullName("Equipe");
        staff.setPasswordHash(passwordEncoder.encode(PASSWORD));
        staff.setRoles(new HashSet<>(List.of(roleRepository.findByName("ROLE_ADMIN").orElseThrow())));
        staff = adminUserRepository.save(staff);
    }

    // ── Lien et code ─────────────────────────────────────────────────

    @Test
    void request_isSilentForUnknownOrDisabledAccounts() throws Exception {
        requestLink("inconnu@example.com").andExpect(status().isOk());
        awa.setActive(false);
        learnerRepository.save(awa);
        requestLink("awa@example.com").andExpect(status().isOk());
        verify(dispatcher, never()).sendAll(anyList(), any());
        assertThat(challengeRepository.count()).isZero();
    }

    @Test
    void link_opensASession_once_andMarksTheEmailVerified() throws Exception {
        requestLink("Awa@Example.com").andExpect(status().isOk());
        OutboundMessage message = sentMessage();
        assertThat(message.type()).isEqualTo("LOGIN_LINK");
        assertThat(message.toEmail()).isEqualTo("awa@example.com");
        assertThat(message.toPhone()).isEqualTo("771234567");   // prêt pour un canal WhatsApp
        String token = extract(message.body(), "token=([A-Za-z0-9_-]+)");

        verifyBody("{\"token\":\"" + token + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.userType").value("LEARNER"));
        assertThat(learnerRepository.findById(awa.getId()).orElseThrow().getEmailVerifiedAt()).isNotNull();

        verifyBody("{\"token\":\"" + token + "\"}").andExpect(status().isBadRequest());   // usage unique
        verifyBody("{\"token\":\"n-importe-quoi\"}").andExpect(status().isBadRequest());
    }

    @Test
    void code_opensASession_andIsStoredOnlyAsAHash() throws Exception {
        requestLink("awa@example.com").andExpect(status().isOk());
        String code = extract(sentMessage().body(), "code sur la page de connexion : (\\d{6})");

        LoginChallenge stored = challengeRepository.findFirstByEmailOrderByCreatedAtDesc("awa@example.com").orElseThrow();
        assertThat(stored.getCodeHash()).hasSize(64).doesNotContain(code);

        verifyBody("{\"email\":\"awa@example.com\",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("awa@example.com"));
        verifyBody("{\"email\":\"awa@example.com\",\"code\":\"" + code + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void code_isBurnedAfterFiveWrongTries_evenIfTheRightOneComesNext() throws Exception {
        requestLink("awa@example.com").andExpect(status().isOk());
        String code = extract(sentMessage().body(), "code sur la page de connexion : (\\d{6})");
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verifyBody("{\"email\":\"awa@example.com\",\"code\":\"" + wrong + "\"}").andExpect(status().isBadRequest());
        }
        verifyBody("{\"email\":\"awa@example.com\",\"code\":\"" + code + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aSecondRequestWithinAMinuteIsIgnored_butAFreshOneReplacesTheFirst() throws Exception {
        requestLink("awa@example.com").andExpect(status().isOk());
        String firstToken = extract(sentMessage().body(), "token=([A-Za-z0-9_-]+)");
        requestLink("awa@example.com").andExpect(status().isOk());
        verify(dispatcher, times(1)).sendAll(anyList(), isNull());

        LoginChallenge first = challengeRepository.findFirstByEmailOrderByCreatedAtDesc("awa@example.com").orElseThrow();
        first.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        challengeRepository.saveAndFlush(first);

        reset(dispatcher);
        requestLink("awa@example.com").andExpect(status().isOk());
        String secondToken = extract(sentMessage().body(), "token=([A-Za-z0-9_-]+)");
        assertThat(secondToken).isNotEqualTo(firstToken);
        verifyBody("{\"token\":\"" + firstToken + "\"}").andExpect(status().isBadRequest());   // l'ancien lien est annulé
        verifyBody("{\"token\":\"" + secondToken + "\"}").andExpect(status().isOk());
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        requestLink("awa@example.com").andExpect(status().isOk());
        String token = extract(sentMessage().body(), "token=([A-Za-z0-9_-]+)");
        LoginChallenge c = challengeRepository.findFirstByEmailOrderByCreatedAtDesc("awa@example.com").orElseThrow();
        c.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        challengeRepository.saveAndFlush(c);

        verifyBody("{\"token\":\"" + token + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void backOfficeAccountsCanUseTheLinkToo_withTheirOwnRoles() throws Exception {
        requestLink("staff@example.com").andExpect(status().isOk());
        String token = extract(sentMessage().body(), "token=([A-Za-z0-9_-]+)");
        verifyBody("{\"token\":\"" + token + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userType").value("ADMIN"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_ADMIN"));
    }

    // ── Verrouillage ─────────────────────────────────────────────────

    @Test
    void fiveBadPasswordsLockThePasswordLogin_andTheRightOneIsRefusedWith429() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("staff@example.com", "mauvais-" + i).andExpect(status().isUnauthorized());
        }
        login("staff@example.com", PASSWORD).andExpect(status().isTooManyRequests());

        // Le verrou expire
        AdminUser locked = adminUserRepository.findById(staff.getId()).orElseThrow();
        locked.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        adminUserRepository.saveAndFlush(locked);
        login("staff@example.com", PASSWORD).andExpect(status().isOk());
        assertThat(adminUserRepository.findById(staff.getId()).orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCounter() throws Exception {
        for (int i = 0; i < 4; i++) login("staff@example.com", "mauvais").andExpect(status().isUnauthorized());
        login("staff@example.com", PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) login("staff@example.com", "mauvais").andExpect(status().isUnauthorized());
        login("staff@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theLinkStillWorksWhileThePasswordIsLocked() throws Exception {
        for (int i = 0; i < 5; i++) login("staff@example.com", "mauvais").andExpect(status().isUnauthorized());
        requestLink("staff@example.com").andExpect(status().isOk());
        String token = extract(sentMessage().body(), "token=([A-Za-z0-9_-]+)");
        verifyBody("{\"token\":\"" + token + "\"}").andExpect(status().isOk());
    }

    // ── Google ───────────────────────────────────────────────────────

    @Test
    void options_tellTheFrontWhichModesToShow() throws Exception {
        mockMvc.perform(get("/api/v1/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value(true))
                .andExpect(jsonPath("$.passwordless").value(true))
                .andExpect(jsonPath("$.google").value(false))
                .andExpect(jsonPath("$.googleClientId").doesNotExist());

        googleEnabled();
        mockMvc.perform(get("/api/v1/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(true))
                .andExpect(jsonPath("$.googleClientId").value("client-123.apps.googleusercontent.com"));
    }

    @Test
    void google_isNotFoundWhenNotConfigured() throws Exception {
        google("jeton").andExpect(status().isNotFound());
    }

    @Test
    void google_opensASessionForAnExistingVerifiedAccount_only() throws Exception {
        googleEnabled();
        when(googleVerifier.verify("bon")).thenReturn(Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("Awa@Example.com", true, "Awa Diop")));
        when(googleVerifier.verify("non-verifie")).thenReturn(Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("awa@example.com", false, "Awa Diop")));
        when(googleVerifier.verify("inconnu")).thenReturn(Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("personne@example.com", true, "X")));
        when(googleVerifier.verify("falsifie")).thenReturn(Optional.empty());

        google("bon").andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value("awa@example.com"));
        google("non-verifie").andExpect(status().isUnauthorized());
        google("inconnu").andExpect(status().isUnauthorized());
        google("falsifie").andExpect(status().isUnauthorized());
        assertThat(learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse("personne@example.com")).isEmpty();   // aucune création
    }

    @Test
    void google_refusesADisabledAccount() throws Exception {
        googleEnabled();
        awa.setActive(false);
        learnerRepository.save(awa);
        when(googleVerifier.verify("bon")).thenReturn(Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("awa@example.com", true, "Awa Diop")));
        google("bon").andExpect(status().isUnauthorized());
    }

    // ── Outils ───────────────────────────────────────────────────────

    private void googleEnabled() {
        when(googleVerifier.enabled()).thenReturn(true);
        when(googleVerifier.clientId()).thenReturn("client-123.apps.googleusercontent.com");
    }

    private ResultActions requestLink(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/passwordless/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions verifyBody(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/passwordless/verify").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions google(String credential) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"" + credential + "\"}"));
    }

    @SuppressWarnings("unchecked")
    private OutboundMessage sentMessage() {
        ArgumentCaptor<List<OutboundMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher, times(1)).sendAll(captor.capture(), isNull());
        assertThat(captor.getValue()).hasSize(1);
        return captor.getValue().get(0);
    }

    private static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertThat(m.find()).as("motif %s dans le message", regex).isTrue();
        return m.group(1);
    }
}
