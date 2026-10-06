package com.modeltech.datamasteryhub.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
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

/** Comptes de back-office : /api/v1/admin/users (SUPER_ADMIN uniquement). */
class AdminUserControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/users";
    /** Compte créé par V4, promu SUPER_ADMIN par l'amorçage de V20. */
    private static final String SEED_ADMIN = "admin@model-technologie.com";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private PartnerRepository partnerRepository;

    @Test
    void onlySuperAdminsCanManageAccounts() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(user("x@test.local", "ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).with(user("x@test.local", "EDITOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void seedAdmin_isPromotedToSuperAdminByTheMigration() throws Exception {
        mockMvc.perform(get(URL).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value(SEED_ADMIN))
                .andExpect(jsonPath("$.data[0].roles").value(org.hamcrest.Matchers.hasItems("ROLE_ADMIN", "ROLE_SUPER_ADMIN")))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    void create_inviteTheNewAccount_andTheLinkLetsItLogIn() throws Exception {
        mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Formateur@Test.local\",\"fullName\":\"Moussa Fall\",\"roles\":[\"trainer\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("formateur@test.local"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_TRAINER"))
                .andExpect(jsonPath("$.data.active").value(true));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendAccountInvitationEmail(
                eq("formateur@test.local"), eq("Moussa Fall"), link.capture(), eq(72), eq(false));
        assertThat(link.getValue()).contains("/admin/reset-password?token=");
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"MotDePasse#2026\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"formateur@test.local\",\"password\":\"MotDePasse#2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userType").value("ADMIN"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_TRAINER"));
    }

    @Test
    void create_partnerAccountsNeedAPartner() throws Exception {
        String body = "{\"email\":\"partenaire@test.local\",\"fullName\":\"Chez Partenaire\",\"roles\":[\"PARTNER\"]%s}";

        mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(",\"partnerId\":\"" + UUID.randomUUID() + "\"")))
                .andExpect(status().isNotFound());

        Partner partner = partnerRepository.save(TestData.partner("acme", "Acme Academy"));
        mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(",\"partnerId\":\"" + partner.getId() + "\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partnerId").value(partner.getId().toString()));
    }

    @Test
    void create_rejectsLearnerRoleUnknownRolesAndDuplicates() throws Exception {
        for (String role : new String[]{"LEARNER", "ROLE_LEARNER", "ROOT"}) {
            mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"a@test.local\",\"fullName\":\"A\",\"roles\":[\"" + role + "\"]}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + SEED_ADMIN + "\",\"fullName\":\"Doublon\",\"roles\":[\"EDITOR\"]}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_changesRolesAndActiveFlag() throws Exception {
        UUID id = createAccount("editeur@test.local", "EDITOR");

        mockMvc.perform(put(URL + "/" + id).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ADMIN\"],\"fullName\":\"Éditeur Promu\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.fullName").value("Éditeur Promu"))
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(get(URL + "/" + UUID.randomUUID()).with(superAdmin())).andExpect(status().isNotFound());
    }

    @Test
    void update_protectsAgainstSelfLockoutAndTheLastSuperAdmin() throws Exception {
        UUID seedId = adminUserRepository.findByEmailAndIsDeletedFalse(SEED_ADMIN).orElseThrow().getId();

        // On ne se désactive pas soi-même, on ne retire pas son propre rôle
        mockMvc.perform(put(URL + "/" + seedId).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isConflict());
        mockMvc.perform(put(URL + "/" + seedId).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\"]}")).andExpect(status().isConflict());

        // Un autre SUPER_ADMIN ne peut pas non plus retirer le dernier SUPER_ADMIN
        mockMvc.perform(put(URL + "/" + seedId).with(user("autre@test.local", "SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isConflict());

        // …mais dès qu'il y en a un second, c'est possible
        UUID second = createAccount("second@test.local", "SUPER_ADMIN");
        mockMvc.perform(put(URL + "/" + seedId).with(user("second@test.local", "SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk());
        assertThat(second).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private UUID createAccount(String email, String role) throws Exception {
        String response = mockMvc.perform(post(URL).with(superAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Test\",\"roles\":[\"" + role + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(response);
        verify(notificationService).sendAccountInvitationEmail(eq(email), anyString(), anyString(), anyInt(), eq(false));
        return UUID.fromString(json.get("data").get("id").asText());
    }

    private static RequestPostProcessor superAdmin() {
        return user(SEED_ADMIN, "SUPER_ADMIN");
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
