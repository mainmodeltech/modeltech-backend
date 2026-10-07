package com.modeltech.datamasteryhub.migration;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rejoue V21 sur des inscriptions « prod » : les statuts historiques et les prix d'affichage
 * doivent traverser la migration intacts, sans montant numérique inventé.
 */
class V21MigrationIT {

    private static final String DB_NAME = "migration_v21";

    @Test
    void keepsExistingRegistrationsAndPrices_andAcceptsTheNewStatuses() throws Exception {
        String url = createEmptyDatabase();
        String user = AbstractIntegrationTest.POSTGRES.getUsername();
        String password = AbstractIntegrationTest.POSTGRES.getPassword();

        flyway(url, user, password, "20").migrate();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            exec(c, "INSERT INTO bootcamps (id, title, slug, price) VALUES "
                    + "('11111111-1111-1111-1111-111111111111', 'Power BI', 'power-bi', '150 000 FCFA')");
            for (String status : List.of("PENDING", "CONFIRMED", "CANCELLED", "COMPLETED")) {
                exec(c, "INSERT INTO registrations (first_name, last_name, email, status) VALUES "
                        + "('A', 'B', '" + status.toLowerCase() + "@x.test', '" + status + "')");
            }
        }

        flyway(url, user, password, null).migrate();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            assertThat(column(c, "SELECT status FROM registrations ORDER BY email"))
                    .containsExactly("CANCELLED", "COMPLETED", "CONFIRMED", "PENDING");
            assertThat(column(c, "SELECT price FROM bootcamps")).containsExactly("150 000 FCFA");
            // Aucun montant numérique deviné à partir du texte d'affichage
            assertThat(column(c, "SELECT price_amount::text FROM bootcamps")).containsExactly((String) null);
            assertThat(column(c, "SELECT currency FROM bootcamps")).containsExactly("XOF");
            assertThat(column(c, "SELECT count(*)::text FROM registrations WHERE total_amount IS NOT NULL OR learner_id IS NOT NULL"))
                    .containsExactly("0");

            for (String status : List.of("PAYMENT_PENDING", "PAYMENT_TO_CONFIRM", "REJECTED")) {
                exec(c, "UPDATE registrations SET status = '" + status + "' WHERE email = 'pending@x.test'");
            }
            assertThatThrownBy(() -> exec(c, "UPDATE registrations SET status = 'BIDON' WHERE email = 'pending@x.test'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("registrations_status_check");
            assertThatThrownBy(() -> exec(c, "UPDATE bootcamps SET price_amount = -1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("bootcamps_price_amount_check");

            // Une seule ligne par échéance, et un seul accès par inscription
            exec(c, "INSERT INTO payments (registration_id, amount, public_token, token_expires_at) "
                    + "SELECT id, 1000, 'tok-1', now() FROM registrations WHERE email = 'pending@x.test'");
            assertThatThrownBy(() -> exec(c, "INSERT INTO payments (registration_id, amount, public_token, token_expires_at) "
                    + "SELECT id, 1000, 'tok-2', now() FROM registrations WHERE email = 'pending@x.test'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uq_payments_installment");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static List<String> column(Connection c, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) values.add(rs.getString(1));
        }
        return values;
    }

    private static Flyway flyway(String url, String user, String password, String target) {
        var config = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private static String createEmptyDatabase() throws SQLException {
        var postgres = AbstractIntegrationTest.POSTGRES;
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB_NAME);
            st.execute("CREATE DATABASE " + DB_NAME);
        }
        return postgres.getJdbcUrl().replaceAll("/[^/?]+(\\?.*)?$", "/" + DB_NAME + "$1");
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
