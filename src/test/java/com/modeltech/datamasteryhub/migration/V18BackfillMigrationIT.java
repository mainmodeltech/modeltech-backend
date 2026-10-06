package com.modeltech.datamasteryhub.migration;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rejoue V18 sur des données « prod » : on migre une base vierge jusqu'à V17, on y
 * insère des bootcamps historiques (titres en double, accents, symboles, supprimé),
 * puis on applique V18 et on vérifie le rétro-remplissage (slug unique, domaine).
 * Ce test n'utilise pas le contexte Spring : il pilote Flyway directement, dans une
 * base dédiée du conteneur PostgreSQL partagé.
 */
class V18BackfillMigrationIT {

    private static final String DB_NAME = "migration_v18";

    @Test
    void backfillsSlugsAndTheDefaultDomainWithoutTouchingExistingData() throws Exception {
        String url = createEmptyDatabase();
        String user = AbstractIntegrationTest.POSTGRES.getUsername();
        String password = AbstractIntegrationTest.POSTGRES.getPassword();

        // 1. État « production » : migrations jusqu'à V17 incluse
        flyway(url, user, password, "17").migrate();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            insertLegacyBootcamp(c, "Power BI", "2026-01-01", false, "150 000 FCFA");
            insertLegacyBootcamp(c, "Power BI", "2026-02-01", false, "150 000 FCFA");  // titre en double
            insertLegacyBootcamp(c, "Bootcamp Excel & Finance", "2026-03-01", false, "120 000 FCFA");
            insertLegacyBootcamp(c, "Données & IA — Édition spéciale !", "2026-04-01", false, null);
            insertLegacyBootcamp(c, "!!!", "2026-05-01", false, null);                  // slug vide
            insertLegacyBootcamp(c, "Ancienne formation", "2026-06-01", true, null);    // supprimée (soft)
        }

        // 2. Déploiement : V18
        flyway(url, user, password, null).migrate();

        // 3. Vérifications
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            Map<String, String> slugByCreationOrder = new LinkedHashMap<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT title, slug, price, delivered_by, level, format, target_roles, partner_id, "
                                 + "(SELECT slug FROM domains d WHERE d.id = b.domain_id) AS domain_slug "
                                 + "FROM bootcamps b ORDER BY created_at")) {
                int i = 0;
                while (rs.next()) {
                    slugByCreationOrder.put(i++ + ":" + rs.getString("title"), rs.getString("slug"));
                    assertThat(rs.getString("domain_slug")).as("domaine de " + rs.getString("title")).isEqualTo("data-bi");
                    assertThat(rs.getString("delivered_by")).isEqualTo("INTERNAL");
                    // Aucune valeur métier inventée
                    assertThat(rs.getString("level")).isNull();
                    assertThat(rs.getString("format")).isNull();
                    assertThat(rs.getArray("target_roles")).isNull();
                    assertThat(rs.getObject("partner_id")).isNull();
                }
            }

            assertThat(slugByCreationOrder).containsExactly(
                    Map.entry("0:Power BI", "power-bi"),
                    Map.entry("1:Power BI", "power-bi-2"),
                    Map.entry("2:Bootcamp Excel & Finance", "bootcamp-excel-finance"),
                    Map.entry("3:Données & IA — Édition spéciale !", "donnees-ia-edition-speciale"),
                    Map.entry("4:!!!", "formation"),
                    Map.entry("5:Ancienne formation", "ancienne-formation"));

            // Colonnes historiques intactes
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT price FROM bootcamps WHERE title = 'Bootcamp Excel & Finance'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("price")).isEqualTo("120 000 FCFA");
            }

            // Le domaine historique est créé une seule fois, visible et non « bientôt »
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*), bool_and(visible), bool_and(NOT coming_soon) "
                         + "FROM domains WHERE slug = 'data-bi'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
                assertThat(rs.getBoolean(2)).isTrue();
                assertThat(rs.getBoolean(3)).isTrue();
            }

            // Contraintes : slug obligatoire et unique, partenaire exigé pour PARTNER
            assertThatThrownBy(() -> exec(c, "INSERT INTO bootcamps (title) VALUES ('Sans slug')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("slug");
            assertThatThrownBy(() -> exec(c, "INSERT INTO bootcamps (title, slug) VALUES ('Doublon', 'power-bi')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uq_bootcamps_slug");
            assertThatThrownBy(() -> exec(c,
                    "INSERT INTO bootcamps (title, slug, delivered_by) VALUES ('Partenaire manquant', 'p', 'PARTNER')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("bootcamps_partner_required_check");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

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

    private static void insertLegacyBootcamp(Connection c, String title, String createdAt,
                                             boolean deleted, String price) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bootcamps (id, title, price, created_at, is_deleted) "
                        + "VALUES (?, ?, ?, ?::timestamptz, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, title);
            ps.setString(3, price);
            ps.setString(4, createdAt + "T00:00:00Z");
            ps.setBoolean(5, deleted);
            ps.executeUpdate();
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
