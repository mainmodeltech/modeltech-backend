package com.modeltech.datamasteryhub.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comptes apprenants : création par l'admin, invitation « définir mon mot de passe »,
 * connexion, isolation du back-office et matrice de rôles sur /api/v1/admin/**.
 */
class AdminLearnerControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/learners";
    private static final String LEARNER_BODY =
            "{\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"Awa.Diop@Example.com\",\"phone\":\"771234567\",\"country\":\"Sénégal\"}";

    @Autowired private ObjectMapper objectMapper;

    // ── Accès ────────────────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    void editorsAndTrainersCannotManageLearners() throws Exception {
        mockMvc.perform(get(URL).with(asRole("EDITOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(asRole("TRAINER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(asRole("ADMIN"))).andExpect(status().isOk());
    }

    // ── Création + invitation ─────────────────────────────────────────

    @Test
    void create_makesAnInvitedAccountWithoutPassword_andSendsTheSetupLink() throws Exception {
        mockMvc.perform(post(URL).with(asRole("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(LEARNER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("awa.diop@example.com"))
                .andExpect(jsonPath("$.data.passwordSet").value(false))
                .andExpect(jsonPath("$.data.active").value(true));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendAccountInvitationEmail(
                eq("awa.diop@example.com"), eq("Awa"), link.capture(), eq(72), eq(true));
        assertThat(link.getValue()).contains("/reinitialiser-mot-de-passe?token=");

        // Tant que le mot de passe n'est pas défini, la connexion est impossible
        login("awa.diop@example.com", "nimportequoi").andExpect(status().isUnauthorized());
    }

    @Test
    void invitationLink_letsTheLearnerSetAPasswordAndLogIn_asALearner() throws Exception {
        String token = createLearnerAndGetToken();

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"MotDePasse#2026\"}"))
                .andExpect(status().isOk());

        String response = login("awa.diop@example.com", "MotDePasse#2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userType").value("LEARNER"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_LEARNER"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(response);
        String jwt = json.get("accessToken").asText();

        // Le lien est à usage unique
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"AutreMotDePasse#1\"}"))
                .andExpect(status().isBadRequest());

        // Le JWT apprenant ouvre /auth/me mais jamais le back-office
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("LEARNER"));
        mockMvc.perform(get("/api/v1/admin/bootcamps").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());

        // Après le premier mot de passe, l'invitation ne peut plus être renvoyée
        UUID id = UUID.fromString(json.get("user").get("id").asText());
        mockMvc.perform(post(URL + "/" + id + "/resend-invitation").with(asRole("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_rejectsDuplicatesAndBackOfficeEmails_with409() throws Exception {
        mockMvc.perform(post(URL).with(asRole("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(LEARNER_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post(URL).with(asRole("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(LEARNER_BODY))
                .andExpect(status().isConflict());
        mockMvc.perform(post(URL).with(asRole("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Faux\",\"email\":\"admin@model-technologie.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_validatesTheBody_with400() throws Exception {
        mockMvc.perform(post(URL).with(asRole("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.firstName").value("Le prénom est obligatoire"))
                .andExpect(jsonPath("$.validationErrors.email").value("L'email est obligatoire"));
    }

    // ── Désactivation ────────────────────────────────────────────────

    @Test
    void deactivate_blocksLoginAndAlreadyIssuedTokens_thenActivateRestoresThem() throws Exception {
        String token = createLearnerAndGetToken();
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"MotDePasse#2026\"}"));
        JsonNode session = objectMapper.readTree(login("awa.diop@example.com", "MotDePasse#2026")
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String jwt = session.get("accessToken").asText();
        UUID id = UUID.fromString(session.get("user").get("id").asText());

        mockMvc.perform(patch(URL + "/" + id + "/deactivate").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        login("awa.diop@example.com", "MotDePasse#2026").andExpect(status().isUnauthorized());
        // Le JWT déjà émis est refusé tout de suite (le filtre recharge le compte à chaque requête)
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(patch(URL + "/" + id + "/activate").with(asRole("ADMIN"))).andExpect(status().isOk());
        login("awa.diop@example.com", "MotDePasse#2026").andExpect(status().isOk());
    }

    @Test
    void get_unknownLearner_is404() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID()).with(asRole("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void resendInvitation_issuesANewLink() throws Exception {
        createLearnerAndGetToken();
        UUID id = UUID.fromString(objectMapper.readTree(
                mockMvc.perform(get(URL).with(asRole("ADMIN"))).andReturn().getResponse()
                        .getContentAsString(StandardCharsets.UTF_8)).get("data").get(0).get("id").asText());

        mockMvc.perform(post(URL + "/" + id + "/resend-invitation").with(asRole("ADMIN"))).andExpect(status().isOk());
        verify(notificationService, org.mockito.Mockito.times(2))
                .sendAccountInvitationEmail(eq("awa.diop@example.com"), anyString(), anyString(), anyInt(), eq(true));
    }

    // ── Matrice de rôles sur le back-office ───────────────────────────

    @Test
    void roleMatrix_onAdminEndpoints() throws Exception {
        // EDITOR : contenu uniquement
        mockMvc.perform(get("/api/v1/admin/domains").with(asRole("EDITOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/registrations").with(asRole("EDITOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/promo-codes").with(asRole("EDITOR"))).andExpect(status().isForbidden());
        // ADMIN : tout sauf la gestion des comptes
        mockMvc.perform(get("/api/v1/admin/registrations").with(asRole("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users").with(asRole("ADMIN"))).andExpect(status().isForbidden());
        // SUPER_ADMIN
        mockMvc.perform(get("/api/v1/admin/users").with(asRole("SUPER_ADMIN"))).andExpect(status().isOk());
        // Un compte sans rôle de back-office (ex. LEARNER) n'entre nulle part
        mockMvc.perform(get("/api/v1/admin/domains").with(asRole("LEARNER"))).andExpect(status().isForbidden());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Crée l'apprenant par l'API admin et retourne le jeton du lien d'invitation. */
    private String createLearnerAndGetToken() throws Exception {
        mockMvc.perform(post(URL).with(asRole("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(LEARNER_BODY))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendAccountInvitationEmail(
                eq("awa.diop@example.com"), anyString(), link.capture(), anyInt(), eq(true));
        return link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static RequestPostProcessor asRole(String role) {
        return SecurityMockMvcRequestPostProcessors.user("tester@test.local").roles(role);
    }
}
