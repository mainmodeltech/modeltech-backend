package com.modeltech.datamasteryhub;

import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Socle de sécurité : pas de fuite publique, secrets masqués et hachés, erreurs 4xx explicites. */
class SecurityBaselineIT extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Test
    void publicPromoValidation_exposesNoReferrerData() throws Exception {
        mockMvc.perform(post("/api/v1/admin/promo-codes").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SECU10\",\"referrerName\":\"Parrain Secret\",\"referrerEmail\":\"parrain@example.com\","
                                + "\"referrerPhone\":\"+221700000000\",\"discountPercent\":10,\"maxUses\":5}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/promo-codes/validate").param("code", "secu10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value("SECU10"))
                .andExpect(jsonPath("$.discountPercent").value(10))
                .andExpect(jsonPath("$.referrerName").doesNotExist())
                .andExpect(jsonPath("$.referrerEmail").doesNotExist())
                .andExpect(jsonPath("$.referrerPhone").doesNotExist())
                .andExpect(jsonPath("$.usageCount").doesNotExist());
    }

    @Test
    void passwordResetTokens_areStoredHashedOnly() {
        String raw = authService.createPasswordResetToken("hash-test@example.com", 60);
        entityManager.flush();   // le test est transactionnel : on force l'écriture avant la lecture JDBC

        String stored = jdbc.queryForObject(
                "select token from password_reset_tokens where email = ?", String.class, "hash-test@example.com");
        assertThat(stored).isNotEqualTo(raw).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void requestDtos_doNotPrintSecretsInToString() {
        LoginRequest request = new LoginRequest();
        request.setEmail("awa@example.com");
        request.setPassword("MotDePasse-Tres-Secret");

        assertThat(request.toString()).contains("awa@example.com").doesNotContain("MotDePasse-Tres-Secret");
    }

    @Test
    void unknownRoute_is404_notAnInternalError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments/route-inexistante").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void databaseConstraintViolation_isA4xxWithAReadableMessage() {
        var handler = new com.modeltech.datamasteryhub.exception.GlobalExceptionHandler();
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/admin/bootcamps");

        var check = handler.handleIntegrity(new org.springframework.dao.DataIntegrityViolationException("x",
                new RuntimeException("new row violates check constraint \"bootcamps_category_check\"")), request);
        assertThat(check.getStatusCode().value()).isEqualTo(400);
        assertThat(check.getBody().message()).contains("n'est pas autorisée").doesNotContain("constraint");

        var duplicate = handler.handleIntegrity(new org.springframework.dao.DataIntegrityViolationException("x",
                new RuntimeException("duplicate key value violates unique constraint")), request);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void anonymousAccessToProtectedRoutes_is401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private static RequestPostProcessor admin() {
        return TestAuth.as("admin@test.local", "ADMIN");
    }
}
