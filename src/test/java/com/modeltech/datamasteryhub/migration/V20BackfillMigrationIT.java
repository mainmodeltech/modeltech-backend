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

/**
 * Rejoue V20 sur des admins « prod » : base migrée jusqu'à V19, avec des comptes dont les rôles
 * ont été posés à la main (ou jamais), puis V20. Aucun admin ne doit se retrouver sans rôle,
 * ceux qui en ont déjà un ne changent pas, et un SUPER_ADMIN est amorcé s'il n'en existait aucun.
 */
class V20BackfillMigrationIT {

    private static final String DB_NAME = "migration_v20";

    @Test
    void backfillsAdminRolesWithoutLockingAnyoneOut_andBootstrapsASuperAdmin() throws Exception {
        String url = createEmptyDatabase();
        String user = AbstractIntegrationTest.POSTGRES.getUsername();
        String password = AbstractIntegrationTest.POSTGRES.getPassword();

        flyway(url, user, password, "19").migrate();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            // V4 a créé admin@model-technologie.com (role = 'ADMIN') ; on le date au plus ancien
            exec(c, "UPDATE admin_users SET created_at = '2020-01-01'");
            exec(c, "INSERT INTO admin_users (email, password_hash, full_name, role, created_at) VALUES "
                    + "('editeur@x.test', 'h', 'Éditeur', 'EDITOR', '2021-01-01'),"
                    + "('sans-role@x.test', 'h', 'Sans rôle', 'inconnu', '2022-01-01'),"
                    + "('deja-role@x.test', 'h', 'Déjà un rôle', 'ADMIN', '2023-01-01')");
            // Rôle posé à la main sur un compte : doit être conservé tel quel
            exec(c, "INSERT INTO admin_user_roles (admin_user_id, role_id) "
                    + "SELECT u.id, r.id FROM admin_users u, roles r "
                    + "WHERE u.email = 'deja-role@x.test' AND r.name = 'ROLE_EDITOR'");
        }

        flyway(url, user, password, null).migrate();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            assertThat(rolesOf(c, "admin@model-technologie.com"))
                    .containsExactly("ROLE_ADMIN", "ROLE_SUPER_ADMIN");   // plus ancien compte → amorçage
            assertThat(rolesOf(c, "editeur@x.test")).containsExactly("ROLE_EDITOR");
            assertThat(rolesOf(c, "sans-role@x.test")).containsExactly("ROLE_ADMIN");   // accès actuel conservé
            assertThat(rolesOf(c, "deja-role@x.test")).containsExactly("ROLE_EDITOR");  // intact

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM roles WHERE name IN "
                         + "('ROLE_LEARNER','ROLE_TRAINER','ROLE_PARTNER')")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
            // Un seul SUPER_ADMIN créé, pas un par compte
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM admin_user_roles x JOIN roles r ON r.id = x.role_id "
                         + "WHERE r.name = 'ROLE_SUPER_ADMIN'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void doesNotBootstrapASuperAdminWhenOneAlreadyExists() throws Exception {
        String url = createEmptyDatabase();
        String user = AbstractIntegrationTest.POSTGRES.getUsername();
        String password = AbstractIntegrationTest.POSTGRES.getPassword();

        flyway(url, user, password, "19").migrate();
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            exec(c, "UPDATE admin_users SET created_at = '2020-01-01'");
            exec(c, "INSERT INTO admin_users (email, password_hash, full_name, role, created_at) "
                    + "VALUES ('patron@x.test', 'h', 'Patron', 'ADMIN', '2024-01-01')");
            exec(c, "INSERT INTO admin_user_roles (admin_user_id, role_id) "
                    + "SELECT u.id, r.id FROM admin_users u, roles r "
                    + "WHERE u.email = 'patron@x.test' AND r.name = 'ROLE_SUPER_ADMIN'");
        }

        flyway(url, user, password, null).migrate();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            assertThat(rolesOf(c, "patron@x.test")).containsExactly("ROLE_SUPER_ADMIN");
            assertThat(rolesOf(c, "admin@model-technologie.com")).containsExactly("ROLE_ADMIN");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static List<String> rolesOf(Connection c, String email) throws SQLException {
        List<String> roles = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT r.name FROM admin_users u "
                     + "JOIN admin_user_roles x ON x.admin_user_id = u.id JOIN roles r ON r.id = x.role_id "
                     + "WHERE u.email = '" + email + "' ORDER BY r.name")) {
            while (rs.next()) roles.add(rs.getString(1));
        }
        return roles;
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
