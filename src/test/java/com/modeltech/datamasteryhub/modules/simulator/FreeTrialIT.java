package com.modeltech.datamasteryhub.modules.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.simulator.entity.*;
import com.modeltech.datamasteryhub.modules.simulator.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Essai gratuit : inscription publique, un seul essai par personne, mini-test limité aux questions échantillon, pas
 * d'entraînement libre, corrections masquées côté serveur au-delà de celles offertes.
 */
@TestPropertySource(properties = {"app.simulator.trial-questions=4", "app.simulator.trial-corrections=2", "app.rate-limit.forms.per-hour=200"})
class FreeTrialIT extends AbstractIntegrationTest {

    private static final String TRIAL = "/api/v1/simulator/trial";
    private static final String LEARNER = "/api/v1/learner/simulator";
    private static final String ADMIN = "/api/v1/admin/simulator";
    private static final String SECRET = "EXPLICATION-SECRETE-ESSAI";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private SimBlueprintRepository blueprints;
    @Autowired private SimItemRepository items;
    @Autowired private SimItemVersionRepository versions;
    @Autowired private AuditRepository audit;

    private SimBlueprint blueprint;

    @BeforeEach
    void content() {
        blueprint = blueprints.findFirstByCertificationCodeAndActiveTrueOrderByVersionDateDesc("PL-300").orElseThrow();
        int n = 0;
        for (SimBlueprintDomain d : blueprint.getDomains()) {
            for (SimBlueprintSkill s : d.getSkills()) publish(s, "PL300-E-" + (++n));
        }
    }

    private void publish(SimBlueprintSkill skill, String code) {
        SimItem item = new SimItem();
        item.setCertification(blueprint.getCertification());
        item.setSkill(skill);
        item.setPublicCode(code);
        item.setCreatedBy("auteur@test.local");
        items.save(item);
        SimItemVersion v = new SimItemVersion();
        v.setItem(item);
        v.setVersionNo(1);
        v.setType(ItemType.SINGLE_CHOICE);
        v.setDifficulty((short) 2);
        v.setStemEn("Question " + code);
        v.setPayload(Map.of("choices", List.of(
                Map.of("id", "A", "text", "Right", "correct", true), Map.of("id", "B", "text", "Wrong 1", "correct", false),
                Map.of("id", "C", "text", "Wrong 2", "correct", false))));
        v.setExplanationFr(SECRET);
        v.setStatus(ItemStatus.PUBLISHED);
        v.setBlueprint(blueprint);
        v.setAuthor("auteur@test.local");
        v.setReviewer("relecteur@test.local");
        v.setPublisher("relecteur@test.local");
        v.setPublishedAt(LocalDateTime.now());
        versions.save(v);
    }

    private static RequestPostProcessor staff() {
        return TestAuth.as("pedago@test.local", "PEDAGOGY");
    }

    private static RequestPostProcessor as(String email) {
        return TestAuth.as(email, "LEARNER");
    }

    private void sync() {
        em.flush();
        em.clear();
    }

    /** Marque 4 questions comme échantillon (une par domaine). */
    private void markSamples() {
        sync();
        jdbc.update("UPDATE sim_item SET trial_sample = TRUE WHERE public_code IN "
                + "(SELECT min(i.public_code) FROM sim_item i JOIN sim_blueprint_skill s ON s.id = i.skill_id WHERE i.public_code LIKE 'PL300-E-%' GROUP BY s.domain_id)");
        em.clear();
    }

    private ResultActions request(String email) throws Exception {
        return mockMvc.perform(post(TRIAL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"" + email + "\",\"consent\":true}"));
    }

    private JsonNode start(String email) throws Exception {
        return json(mockMvc.perform(post(LEARNER + "/attempts").with(as(email)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"EXAM\"}")).andExpect(status().isCreated())).at("/data");
    }

    private void answer(String email, JsonNode attempt, int index, String choice) throws Exception {
        mockMvc.perform(put(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/answers/" + attempt.at("/questions/" + index + "/ref").asText())
                .with(as(email)).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"" + choice + "\"]}}")).andExpect(status().isOk());
    }

    // ── Inscription publique ─────────────────────────────────────────

    @Test
    void aVisitorGetsAnAccountAndOneTrial_andAskingAgainChangesNothing() throws Exception {
        request("nouvelle@test.local").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learners WHERE email = 'nouvelle@test.local'", Integer.class)).isEqualTo(1);
        Map<String, Object> grant = jdbc.queryForMap("SELECT plan_code, exam_attempts_total, granted_by FROM sim_access_grant");
        assertThat(grant).containsEntry("plan_code", "TRIAL").containsEntry("exam_attempts_total", 1).containsEntry("granted_by", "essai-gratuit");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_messages WHERE type = 'SIMULATOR_TRIAL'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learners WHERE email = 'nouvelle@test.local' AND password_hash IS NULL", Integer.class)).isEqualTo(1);

        // même réponse, aucun doublon : ni compte, ni droit, ni prospect
        request("nouvelle@test.local").andExpect(status().isOk());
        request("NOUVELLE@test.local").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learners WHERE email = 'nouvelle@test.local'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sim_access_grant", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_messages WHERE type = 'SIMULATOR_TRIAL'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theFormIsValidated_andStaffAddressesAreSilentlyIgnored() throws Exception {
        mockMvc.perform(post(TRIAL).contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Awa\",\"email\":\"x@test.local\",\"consent\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(TRIAL).contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Awa\",\"email\":\"pas-un-mail\",\"consent\":true}"))
                .andExpect(status().isBadRequest());
        request("jetable@mailinator.com").andExpect(status().isBadRequest());

        // l'adresse d'un compte du back-office reçoit la même réponse neutre et ne crée rien
        String staffEmail = jdbc.queryForObject("SELECT email FROM admin_users WHERE is_deleted = FALSE LIMIT 1", String.class);
        request(staffEmail).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sim_access_grant", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learners WHERE lower(email) = lower(?)", Integer.class, staffEmail)).isZero();
    }

    // ── Mini-test ────────────────────────────────────────────────────

    @Test
    void theTrialIsATimedMiniTest_madeOfSampleQuestionsOnly_withoutFreeStudy() throws Exception {
        markSamples();
        request("essai@test.local").andExpect(status().isOk());

        JsonNode catalog = json(mockMvc.perform(get(LEARNER + "/catalog").with(as("essai@test.local"))).andExpect(status().isOk())).at("/data");
        assertThat(catalog.at("/trial").asBoolean()).isTrue();
        assertThat(catalog.at("/domains")).isEmpty();             // la taille de la banque n'est pas divulguée
        assertThat(catalog.at("/durationMinutes").asInt()).isEqualTo(15);
        assertThat(catalog.at("/examQuestions").asInt()).isEqualTo(4);
        assertThat(catalog.at("/trialCorrections").asInt()).isEqualTo(2);

        // l'entraînement libre est réservé aux forfaits payants
        mockMvc.perform(post(LEARNER + "/attempts").with(as("essai@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"STUDY\",\"domain\":\"PRE\"}")).andExpect(status().isForbidden());

        JsonNode attempt = start("essai@test.local");
        assertThat(attempt.at("/trial").asBoolean()).isTrue();
        assertThat(attempt.at("/totalQuestions").asInt()).isEqualTo(4);
        LocalDateTime deadline = LocalDateTime.parse(attempt.at("/deadlineAt").asText());
        LocalDateTime now = LocalDateTime.parse(attempt.at("/serverNow").asText());
        assertThat(Duration.between(now, deadline).toMinutes()).isBetween(13L, 15L);
        sync();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sim_exam_form_item fi JOIN sim_item_version v ON v.id = fi.item_version_id "
                + "JOIN sim_item i ON i.id = v.item_id WHERE NOT i.trial_sample", Integer.class)).isZero();
        assertThat(attempt.toString()).doesNotContain(SECRET).doesNotContain("correct");

        // un seul essai
        mockMvc.perform(post(LEARNER + "/attempts").with(as("essai@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void theReportOnlyReleasesTheOfferedCorrections_andNothingElseLeaks() throws Exception {
        markSamples();
        request("essai@test.local").andExpect(status().isOk());
        JsonNode attempt = start("essai@test.local");
        // 4 questions : la 1re juste, les 3 autres fausses
        answer("essai@test.local", attempt, 0, "A");
        for (int i = 1; i < 4; i++) answer("essai@test.local", attempt, i, "B");

        ResultActions submitted = mockMvc.perform(post(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/submit").with(as("essai@test.local")))
                .andExpect(status().isOk());
        String body = submitted.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode report = objectMapper.readTree(body).at("/data");

        assertThat(report.at("/trial").asBoolean()).isTrue();
        assertThat(report.at("/summary/passed").isNull()).isTrue();       // un mini-test n'est pas un examen « réussi / échoué »
        assertThat(report.at("/summary/scorePercent").asDouble()).isEqualTo(25.0);
        assertThat(report.at("/domains")).isNotEmpty();                   // le diagnostic par domaine reste offert
        assertThat(report.at("/lockedCount").asInt()).isEqualTo(2);

        int unlocked = 0;
        for (JsonNode q : report.at("/questions")) {
            if (q.at("/locked").asBoolean()) {
                assertThat(q.at("/correctAnswer").isNull()).isTrue();
                assertThat(q.at("/explanationFr").isNull()).isTrue();
                assertThat(q.at("/score").isNull()).isTrue();
            } else {
                unlocked++;
                assertThat(q.at("/explanationFr").asText()).isEqualTo(SECRET);
                assertThat(q.at("/correctAnswer/selected/0").asText()).isEqualTo("A");
                assertThat(q.at("/score").asDouble()).isZero();            // les corrections offertes sont celles des erreurs
            }
        }
        assertThat(unlocked).isEqualTo(2);
        assertThat(body.split(SECRET, -1).length - 1).isEqualTo(2);        // l'explication secrète n'apparaît que deux fois

        // même contenu en relisant le rapport
        String again = mockMvc.perform(get(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/report").with(as("essai@test.local")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(again.split(SECRET, -1).length - 1).isEqualTo(2);
    }

    @Test
    void withoutSampleQuestions_theTrialIsUnavailable_andDoesNotBurnTheAttempt() throws Exception {
        request("essai@test.local").andExpect(status().isOk());
        mockMvc.perform(post(LEARNER + "/attempts").with(as("essai@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\"}"))
                .andExpect(status().isConflict());
        sync();
        assertThat(jdbc.queryForObject("SELECT exam_attempts_used FROM sim_access_grant", Integer.class)).isZero();
    }

    @Test
    void aPaidAccessTurnsTheTrialIntoTheFullSimulator() throws Exception {
        markSamples();
        request("essai@test.local").andExpect(status().isOk());
        mockMvc.perform(post(ADMIN + "/grants").with(staff()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerEmail\":\"essai@test.local\",\"plan\":\"PRO\",\"days\":30,\"examAttempts\":2,\"reason\":\"Paiement Wave reçu\"}"))
                .andExpect(status().isCreated());
        JsonNode catalog = json(mockMvc.perform(get(LEARNER + "/catalog").with(as("essai@test.local"))).andExpect(status().isOk())).at("/data");
        assertThat(catalog.at("/trial").asBoolean()).isFalse();
        assertThat(catalog.at("/domains")).hasSize(4);
        mockMvc.perform(post(LEARNER + "/attempts").with(as("essai@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"STUDY\",\"domain\":\"PRE\"}")).andExpect(status().isCreated());
    }

    // ── Administration ───────────────────────────────────────────────

    @Test
    void onlyAPublishedQuestionCanBeASample_andOnlyAManagerChoosesThem() throws Exception {
        mockMvc.perform(put(ADMIN + "/items/PL300-E-1/trial-sample").with(TestAuth.as("adm@test.local", "ADMISSIONS"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":true}")).andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN + "/items/PL300-E-1/trial-sample").with(staff()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":true}"))
                .andExpect(status().isOk());
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("SIM_TRIAL_SAMPLE_SET");

        JsonNode filtered = json(mockMvc.perform(get(ADMIN + "/items?trialSample=true").with(staff())).andExpect(status().isOk()));
        assertThat(filtered.at("/data")).hasSize(1);
        assertThat(filtered.at("/data/0/trialSample").asBoolean()).isTrue();

        // une question retirée ne peut plus être ajoutée
        jdbc.update("UPDATE sim_item_version SET status = 'RETIRED', retired_at = NOW(), retire_reason = 'test' WHERE item_id = (SELECT id FROM sim_item WHERE public_code = 'PL300-E-2')");
        sync();
        mockMvc.perform(put(ADMIN + "/items/PL300-E-2/trial-sample").with(staff()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":true}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(ADMIN + "/items/INCONNU/trial-sample").with(staff()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theFreeTrialCannotBeGrantedTwiceToTheSamePerson() throws Exception {
        request("essai@test.local").andExpect(status().isOk());
        mockMvc.perform(post(ADMIN + "/grants").with(staff()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerEmail\":\"essai@test.local\",\"plan\":\"TRIAL\",\"days\":7,\"examAttempts\":1,\"reason\":\"doublon\"}"))
                .andExpect(status().isConflict());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
