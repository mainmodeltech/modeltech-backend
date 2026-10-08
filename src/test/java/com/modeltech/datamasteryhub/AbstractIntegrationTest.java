package com.modeltech.datamasteryhub;

import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base des tests d'intégration : contexte Spring complet, MockMvc et un vrai
 * PostgreSQL (Testcontainers) sur lequel Flyway rejoue toutes les migrations.
 * Le conteneur est partagé par toutes les classes de test (démarré une seule fois).
 * Chaque test est transactionnel : ses données sont annulées à la fin.
 *
 * Nécessite un démon Docker démarré.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        // Secret HMAC >= 512 bits (HS512) — valeur de test uniquement
        registry.add("JWT_SECRET", () -> "test-only-jwt-secret-".concat("x".repeat(80)));
        registry.add("MINIO_ROOT_USER", () -> "test-user");
        registry.add("MINIO_ROOT_PASSWORD", () -> "test-password");
        // Tous les tests partagent la même IP (127.0.0.1) : la limite des formulaires est relevée.
        // Le comportement de la limite est couvert par IpRateLimiterTest et RateLimitedFormsIT.
        registry.add("app.rate-limit.forms.per-hour", () -> "1000");
        registry.add("app.rate-limit.login.per-hour", () -> "1000");
        registry.add("app.rate-limit.verify.per-hour", () -> "1000");
    }

    @Autowired
    protected MockMvc mockMvc;

    /** Aucun e-mail/Slack réel pendant les tests ; les tests vérifient les appels. */
    @MockBean
    protected NotificationService notificationService;
}
