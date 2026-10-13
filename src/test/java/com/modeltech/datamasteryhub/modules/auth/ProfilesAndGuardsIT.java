package com.modeltech.datamasteryhub.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profils et droits : gestion des profils par la direction (sans élévation de privilèges possible), et contrôles « par
 * champ » (tarifs, part de revenu des partenaires, dérogation de prix) qui complètent la table des routes.
 */
class ProfilesAndGuardsIT extends AbstractIntegrationTest {

    private static final String PROFILES = "/api/v1/admin/profiles";
    private static final String USERS = "/api/v1/admin/users";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;

    private final RequestPostProcessor direction = TestAuth.as("direction@test.local", "SUPER_ADMIN");

    private Bootcamp bootcamp;
    private BootcampSession session;

    @BeforeEach
    void setUp() {
        bootcamp = TestData.bootcamp("power-bi", "Power BI", null);
        bootcamp.setPrice("150 000 FCFA");
        bootcamp.setPriceAmount(150_000L);
        bootcamp = bootcampRepository.save(bootcamp);
        session = sessionRepository.save(TestData.session(bootcamp, "Cohorte 1", LocalDate.now().plusDays(30), SessionStatus.OPEN));
    }

    // ── Gestion des profils ──────────────────────────────────────────

    @Test
    void directionListsTheShippedProfilesWithTheirRights() throws Exception {
        JsonNode profiles = json(mockMvc.perform(get(PROFILES).with(direction)).andExpect(status().isOk())).get("data");
        JsonNode finance = find(profiles, "ROLE_FINANCE");
        assertThat(finance.get("label").asText()).isEqualTo("Finance");
        assertThat(finance.get("system").asBoolean()).isTrue();
        assertThat(texts(finance.get("permissions"))).contains("PAYMENTS_REFUND", "PRICING_MANAGE").doesNotContain("ACCOUNTS_MANAGE");
        JsonNode dir = find(profiles, "ROLE_SUPER_ADMIN");
        assertThat(dir.get("allPermissions").asBoolean()).isTrue();
        assertThat(dir.get("permissions").size()).isEqualTo(Permission.values().length);
        assertThat(profiles.findValuesAsText("name")).doesNotContain("ROLE_LEARNER");

        JsonNode catalogue = json(mockMvc.perform(get(PROFILES + "/permissions").with(direction)).andExpect(status().isOk())).get("data");
        assertThat(catalogue.size()).isEqualTo(Permission.values().length);
    }

    @Test
    void onlyDirectionManagesProfiles() throws Exception {
        for (String profile : new String[]{"ADMIN", "FINANCE", "EDITOR", "AUDITOR"}) {
            mockMvc.perform(get(PROFILES).with(TestAuth.as("x@test.local", profile))).andExpect(status().isForbidden());
        }
    }

    @Test
    void directionCreatesEditsAndDeletesACustomProfile() throws Exception {
        JsonNode created = json(mockMvc.perform(post(PROFILES).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Responsable Partenariats\",\"description\":\"Suit les partenaires\",\"permissions\":[\"PARTNERS_VIEW\",\"PARTNERS_MANAGE\"]}"))
                .andExpect(status().isCreated())).get("data");
        String id = created.get("id").asText();
        assertThat(created.get("name").asText()).isEqualTo("ROLE_RESPONSABLE_PARTENARIATS");
        assertThat(created.get("system").asBoolean()).isFalse();

        mockMvc.perform(put(PROFILES + "/" + id).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"PARTNERS_VIEW\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions.length()").value(1));

        // Un doublon de nom est refusé, un droit inconnu aussi
        mockMvc.perform(post(PROFILES).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Responsable partenariats\",\"permissions\":[]}")).andExpect(status().isConflict());
        mockMvc.perform(put(PROFILES + "/" + id).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"INCONNU\"]}")).andExpect(status().isBadRequest());

        mockMvc.perform(delete(PROFILES + "/" + id).with(direction)).andExpect(status().isOk());
        JsonNode after = json(mockMvc.perform(get(PROFILES).with(direction))).get("data");
        assertThat(after.findValuesAsText("id")).doesNotContain(id);
    }

    @Test
    void shippedProfilesCanBeAdjustedAndRestored_butNeverDeleted_andDirectionIsFixed() throws Exception {
        JsonNode finance = find(json(mockMvc.perform(get(PROFILES).with(direction))).get("data"), "ROLE_FINANCE");
        String id = finance.get("id").asText();
        int before = finance.get("permissions").size();

        mockMvc.perform(put(PROFILES + "/" + id).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"PAYMENTS_VIEW\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions.length()").value(1));
        mockMvc.perform(post(PROFILES + "/" + id + "/reset").with(direction))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions.length()").value(before));
        mockMvc.perform(delete(PROFILES + "/" + id).with(direction)).andExpect(status().isConflict());

        String directionId = find(json(mockMvc.perform(get(PROFILES).with(direction))).get("data"), "ROLE_SUPER_ADMIN").get("id").asText();
        mockMvc.perform(put(PROFILES + "/" + directionId).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}")).andExpect(status().isConflict());
    }

    @Test
    void aProfileEditorCannotGrantRightsItDoesNotHold() throws Exception {
        var limited = TestAuth.withPermissions("rh@test.local", Permission.PROFILES_MANAGE, Permission.PAYMENTS_VIEW);
        mockMvc.perform(post(PROFILES).with(limited).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Sous-direction\",\"permissions\":[\"PAYMENTS_VIEW\",\"PAYMENTS_REFUND\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PROFILES).with(limited).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Lecteur paiements\",\"permissions\":[\"PAYMENTS_VIEW\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void accountsCanOnlyReceiveProfilesWithinTheGrantersOwnRights() throws Exception {
        var accountsOnly = TestAuth.withPermissions("rh@test.local", Permission.ACCOUNTS_MANAGE, Permission.SESSIONS_VIEW);
        // Formateur = SESSIONS_VIEW + SESSIONS_TRACK : SESSIONS_TRACK manque
        mockMvc.perform(post(USERS).with(accountsOnly).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nouveau@test.local\",\"fullName\":\"Nouveau\",\"roles\":[\"TRAINER\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(USERS).with(accountsOnly).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nouveau@test.local\",\"fullName\":\"Nouveau\",\"roles\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isForbidden());
        // La direction peut tout attribuer, y compris plusieurs profils cumulés et un profil créé par elle
        mockMvc.perform(post(USERS).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"resp-admin@test.local\",\"fullName\":\"Resp Admin\",\"roles\":[\"FINANCE\",\"ADMISSIONS\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.roles.length()").value(2));
        // Un apprenant n'est pas un profil de back-office
        mockMvc.perform(post(USERS).with(direction).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@test.local\",\"fullName\":\"X\",\"roles\":[\"LEARNER\"]}"))
                .andExpect(status().isBadRequest());
    }

    // ── Tarifs ───────────────────────────────────────────────────────

    @Test
    void catalogueEditorsCannotChangeTheFormationPrice_butTheOtherFieldsStillWork() throws Exception {
        var pedagogy = TestAuth.as("pedago@test.local", "PEDAGOGY");
        String url = "/api/v1/admin/bootcamps/" + bootcamp.getId();

        // Le formulaire renvoie le prix inchangé : accepté
        mockMvc.perform(put(url).with(pedagogy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Power BI avancé\",\"price\":\"150 000 FCFA\",\"priceAmount\":150000}"))
                .andExpect(status().isOk());
        assertThat(bootcampRepository.findById(bootcamp.getId()).orElseThrow().getTitle()).isEqualTo("Power BI avancé");

        mockMvc.perform(put(url).with(pedagogy).contentType(MediaType.APPLICATION_JSON).content("{\"priceAmount\":99000}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/bootcamps").with(pedagogy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouvelle\",\"priceAmount\":50000}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/bootcamps").with(pedagogy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouvelle sans prix\"}")).andExpect(status().isCreated());
    }

    @Test
    void financeChangesPricesOnly() throws Exception {
        var finance = TestAuth.as("fin@test.local", "FINANCE");
        String url = "/api/v1/admin/bootcamps/" + bootcamp.getId();
        mockMvc.perform(put(url).with(finance).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titre piraté\",\"priceAmount\":120000}"))
                .andExpect(status().isOk());
        Bootcamp reloaded = bootcampRepository.findById(bootcamp.getId()).orElseThrow();
        assertThat(reloaded.getPriceAmount()).isEqualTo(120_000L);
        assertThat(reloaded.getTitle()).isEqualTo("Power BI");   // le reste du formulaire est ignoré

        // Création, suppression, publication : réservées au catalogue
        mockMvc.perform(post("/api/v1/admin/bootcamps").with(finance).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(url).with(finance)).andExpect(status().isForbidden());
    }

    @Test
    void sessionPricesFollowTheSameRule() throws Exception {
        String url = "/api/v1/admin/bootcamps/sessions/" + session.getId();
        mockMvc.perform(put(url).with(TestAuth.as("pedago@test.local", "PEDAGOGY")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceOverrideAmount\":100000}")).andExpect(status().isForbidden());
        mockMvc.perform(put(url).with(TestAuth.as("pedago@test.local", "PEDAGOGY")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"Dakar\"}")).andExpect(status().isOk());
        mockMvc.perform(put(url).with(TestAuth.as("fin@test.local", "FINANCE")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceOverrideAmount\":100000,\"location\":\"Ignoré\"}")).andExpect(status().isOk());
        BootcampSession reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getPriceOverrideAmount()).isEqualTo(100_000L);
        assertThat(reloaded.getLocation()).isEqualTo("Dakar");
    }

    // ── Part de revenu des partenaires ───────────────────────────────

    @Test
    void partnerRevenueShareNeedsItsOwnRight() throws Exception {
        var pedagogy = TestAuth.as("pedago@test.local", "PEDAGOGY");   // gère les partenaires, pas leur part de revenu
        mockMvc.perform(post("/api/v1/admin/partners").with(pedagogy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agile\",\"revenueSharePercent\":35}")).andExpect(status().isForbidden());
        String id = json(mockMvc.perform(post("/api/v1/admin/partners").with(pedagogy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agile\"}")).andExpect(status().isCreated())).get("data").get("id").asText();
        mockMvc.perform(put("/api/v1/admin/partners/" + id).with(pedagogy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revenueSharePercent\":40}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/partners/" + id).with(TestAuth.as("fin@test.local", "FINANCE")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revenueSharePercent\":40}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenueSharePercent").value(40));
        // Une modification qui ne touche pas à la part (même valeur renvoyée par le formulaire) reste permise
        mockMvc.perform(put("/api/v1/admin/partners/" + id).with(pedagogy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Cabinet\",\"revenueSharePercent\":40.00}")).andExpect(status().isOk());
    }

    // ── Dérogation de prix à l'acceptation ───────────────────────────

    @Test
    void acceptingAtTheListPriceNeedsNoOverride_butANegotiatedAmountDoes() throws Exception {
        var admissions = TestAuth.as("commerciale@test.local", "ADMISSIONS");
        Registration a = registration("a@example.com");
        accept(a, admissions, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(150_000));

        Registration b = registration("b@example.com");
        accept(b, admissions, "{\"totalAmount\":100000}").andExpect(status().isForbidden());
        accept(b, admissions, "{\"totalAmount\":150000}").andExpect(status().isOk());   // le tarif lui-même n'est pas une dérogation

        Registration c = registration("c@example.com");
        accept(c, TestAuth.as("fin@test.local", "FINANCE"), "{\"totalAmount\":100000}")
                .andExpect(status().isForbidden());   // la finance n'accepte pas les candidatures
        accept(c, TestAuth.as("resp@test.local", "FINANCE", "ADMISSIONS"), "{\"totalAmount\":100000}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(100_000));
    }

    // ── Tableau de bord ──────────────────────────────────────────────

    @Test
    void activityDashboardHidesMoneyFromAdmissions() throws Exception {
        JsonNode admissions = json(mockMvc.perform(get("/api/v1/admin/stats/overview").with(TestAuth.as("c@test.local", "ADMISSIONS")))
                .andExpect(status().isOk())).get("data");
        assertThat(admissions.get("financeVisible").asBoolean()).isFalse();
        assertThat(admissions.has("revenue")).isFalse();
        assertThat(admissions.get("registrations")).isNotNull();

        JsonNode finance = json(mockMvc.perform(get("/api/v1/admin/stats/overview").with(TestAuth.as("f@test.local", "FINANCE")))
                .andExpect(status().isOk())).get("data");
        assertThat(finance.get("financeVisible").asBoolean()).isTrue();
        assertThat(finance.has("revenue")).isTrue();
    }

    @Test
    void menuBadgesOnlyCountWhatThePersonCanHandle() throws Exception {
        JsonNode trainer = json(mockMvc.perform(get("/api/v1/admin/stats/actions").with(TestAuth.as("t@test.local", "TRAINER")))
                .andExpect(status().isOk())).get("data");
        assertThat(trainer.has("openQuestions")).isTrue();
        assertThat(trainer.has("newApplications")).isFalse();
        assertThat(trainer.has("paymentsToConfirm")).isFalse();
        assertThat(trainer.has("unreadMessages")).isFalse();

        JsonNode finance = json(mockMvc.perform(get("/api/v1/admin/stats/actions").with(TestAuth.as("f@test.local", "FINANCE")))
                .andExpect(status().isOk())).get("data");
        assertThat(finance.has("paymentsToConfirm")).isTrue();
        assertThat(finance.has("unreadMessages")).isFalse();
    }

    // ── utilitaires ──────────────────────────────────────────────────

    private Registration registration(String email) {
        return registrationRepository.save(TestData.registration(email, bootcamp, session, null));
    }

    private ResultActions accept(Registration reg, RequestPostProcessor as, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/registrations/" + reg.getId() + "/accept").with(as)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode find(JsonNode profiles, String name) {
        for (JsonNode p : profiles) if (name.equals(p.get("name").asText())) return p;
        throw new AssertionError("profil absent : " + name);
    }

    private static java.util.List<String> texts(JsonNode array) {
        java.util.List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
