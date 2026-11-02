package com.modeltech.datamasteryhub.modules.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.simulator.entity.*;
import com.modeltech.datamasteryhub.modules.simulator.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Moteur de tentative : droits d'usage, assemblage, examen chronométré, notation, correction, isolation entre apprenants,
 * quota. Chaque test ouvre ses propres données (transaction annulée) ; l'examen compte 8 questions pour rester léger.
 */
@TestPropertySource(properties = "app.simulator.exam-questions=8")
class AttemptEngineIT extends AbstractIntegrationTest {

    private static final String LEARNER = "/api/v1/learner/simulator";
    private static final String ADMIN = "/api/v1/admin/simulator";
    private static final String SECRET_EXPLANATION = "EXPLICATION-SECRETE-NE-DOIT-PAS-FUITER";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private LearnerRepository learnerRepository;
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
            for (SimBlueprintSkill s : d.getSkills()) {
                for (int i = 0; i < 2; i++) publish(s, "PL300-T-" + (++n));   // 22 questions publiées, de quoi composer plusieurs examens
            }
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
                Map.of("id", "A", "text", "Right " + code, "correct", true), Map.of("id", "B", "text", "Wrong 1", "correct", false),
                Map.of("id", "C", "text", "Wrong 2", "correct", false), Map.of("id", "D", "text", "Wrong 3", "correct", false))));
        v.setExplanationFr(SECRET_EXPLANATION);
        v.setStatus(ItemStatus.PUBLISHED);
        v.setBlueprint(blueprint);
        v.setAuthor("auteur@test.local");
        v.setReviewer("relecteur@test.local");
        v.setPublisher("relecteur@test.local");
        v.setPublishedAt(LocalDateTime.now());
        versions.save(v);
    }

    private Learner learner(String email) {
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName("Awa");
        l.setLastName("Diop");
        return learnerRepository.save(l);
    }

    private static RequestPostProcessor as(String email) {
        return TestAuth.as(email, "LEARNER");
    }

    private static RequestPostProcessor staff() {
        return TestAuth.as("pedago@test.local", "PEDAGOGY");
    }

    private void grant(String email, int days, int exams) throws Exception {
        mockMvc.perform(post(ADMIN + "/grants").with(staff()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerEmail\":\"" + email + "\",\"plan\":\"PRO\",\"days\":" + days + ",\"examAttempts\":" + exams
                        + ",\"reason\":\"Test de recette\"}")).andExpect(status().isCreated());
    }

    private JsonNode start(String email, String mode) throws Exception {
        return json(mockMvc.perform(post(LEARNER + "/attempts").with(as(email)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"" + mode + "\"}")).andExpect(status().isCreated())).at("/data");
    }

    private void answerAll(String email, JsonNode attempt, String choice) throws Exception {
        for (JsonNode q : attempt.at("/questions")) {
            mockMvc.perform(put(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/answers/" + q.at("/ref").asText()).with(as(email))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"" + choice + "\"]}}")).andExpect(status().isOk());
        }
    }

    // ── Droits d'usage ───────────────────────────────────────────────

    @Test
    void withoutAnActiveAccess_nothingStarts_andOnlyStaffWithTheRightGrants() throws Exception {
        learner("awa@test.local");
        mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"EXAM\"}")).andExpect(status().isForbidden());

        // octroi : réservé à SIMULATOR_MANAGE, motif obligatoire, apprenant connu, tracé dans l'audit
        mockMvc.perform(post(ADMIN + "/grants").with(TestAuth.as("adm@test.local", "ADMISSIONS")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerEmail\":\"awa@test.local\",\"plan\":\"PRO\",\"days\":30,\"examAttempts\":2,\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN + "/grants").with(staff()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerEmail\":\"awa@test.local\",\"plan\":\"PRO\",\"days\":30,\"examAttempts\":2}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN + "/grants").with(staff()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerEmail\":\"inconnu@test.local\",\"plan\":\"PRO\",\"days\":30,\"examAttempts\":2,\"reason\":\"x\"}")).andExpect(status().isNotFound());
        grant("awa@test.local", 30, 2);
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("SIM_ACCESS_GRANTED");

        JsonNode catalog = json(mockMvc.perform(get(LEARNER + "/catalog").with(as("awa@test.local"))).andExpect(status().isOk())).at("/data");
        assertThat(catalog.at("/access/0/examAttemptsRemaining").asInt()).isEqualTo(2);
        assertThat(catalog.at("/domains")).hasSize(4);
        assertThat(catalog.at("/domains/0/publishedQuestions").asInt()).isEqualTo(6);
        // un compte du personnel n'entre pas dans l'espace apprenant
        mockMvc.perform(get(LEARNER + "/catalog").with(staff())).andExpect(status().isForbidden());
    }

    @Test
    void aRevokedOrExpiredAccessStopsWorking() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 2);
        String id = json(mockMvc.perform(get(ADMIN + "/grants").with(staff()))).at("/data/0/id").asText();
        mockMvc.perform(post(ADMIN + "/grants/" + id + "/revoke").with(staff()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Remboursé\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(post(ADMIN + "/grants/" + id + "/revoke").with(staff()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"encore\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"STUDY\"}")).andExpect(status().isForbidden());
    }

    // ── Examen ───────────────────────────────────────────────────────

    @Test
    void anExam_followsTheBlueprint_hasAServerDeadline_andNeverLeaksTheAnswers() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 3);
        ResultActions started = mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"EXAM\"}")).andExpect(status().isCreated());
        String body = started.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode attempt = objectMapper.readTree(body).at("/data");

        assertThat(attempt.at("/totalQuestions").asInt()).isEqualTo(8);
        assertThat(attempt.at("/status").asText()).isEqualTo("IN_PROGRESS");
        // échéance = 100 minutes décidées par le serveur
        LocalDateTime deadline = LocalDateTime.parse(attempt.at("/deadlineAt").asText());
        LocalDateTime now = LocalDateTime.parse(attempt.at("/serverNow").asText());
        assertThat(java.time.Duration.between(now, deadline).toMinutes()).isBetween(98L, 100L);
        // répartition du blueprint : 2 questions par domaine pour 8 questions
        sync();
        Map<String, Integer> perDomain = new HashMap<>();
        jdbc.queryForList("SELECT domain_code, count(*) AS n FROM sim_exam_form_item GROUP BY domain_code")
                .forEach(r -> perDomain.put((String) r.get("domain_code"), ((Number) r.get("n")).intValue()));
        assertThat(perDomain).containsOnlyKeys("PRE", "MOD", "VIS", "SEC").allSatisfy((k, v) -> assertThat(v).isEqualTo(2));

        // AUCUN corrigé ni explication dans ce que reçoit le navigateur
        assertNoAnswerKeys(attempt);
        assertThat(body).doesNotContain(SECRET_EXPLANATION);
        for (JsonNode q : attempt.at("/questions")) {
            assertThat(q.at("/payload/choices")).hasSize(4);
            assertThat(q.at("/payload/choices/0").fieldNames()).toIterable().containsExactlyInAnyOrder("id", "text");
        }

        // en examen, enregistrer une réponse ne renvoie aucune correction
        JsonNode first = attempt.at("/questions/0");
        String put = mockMvc.perform(put(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/answers/" + first.at("/ref").asText()).with(as("awa@test.local"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"A\"]},\"flagged\":true,\"timeSpentSeconds\":42}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(put).doesNotContain(SECRET_EXPLANATION).doesNotContain("correctAnswer");
        assertThat(objectMapper.readTree(put).at("/data/feedback").isNull()).isTrue();
        // reprise : la réponse et le marquage sont restitués, toujours sans corrigé
        JsonNode resumed = json(mockMvc.perform(get(LEARNER + "/attempts/" + attempt.at("/id").asText()).with(as("awa@test.local")))).at("/data");
        assertThat(resumed.at("/answered").asInt()).isEqualTo(1);
        assertNoAnswerKeys(resumed);
        // le rapport n'existe pas tant que l'examen n'est pas remis
        mockMvc.perform(get(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/report").with(as("awa@test.local"))).andExpect(status().isConflict());
        // une seule tentative d'examen à la fois
        mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void submitting_scoresTheExam_byDomain_andRevealsTheCorrection() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 3);
        JsonNode attempt = start("awa@test.local", "EXAM");
        answerAll("awa@test.local", attempt, "A");   // toutes les bonnes réponses (A dans les données de test)

        JsonNode report = json(mockMvc.perform(post(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/submit").with(as("awa@test.local")))
                .andExpect(status().isOk())).at("/data");
        assertThat(report.at("/summary/scorePercent").asDouble()).isEqualTo(100.0);
        assertThat(report.at("/summary/scaledScore").asInt()).isEqualTo(1000);
        assertThat(report.at("/summary/passed").asBoolean()).isTrue();
        assertThat(report.at("/estimated").asBoolean()).isTrue();
        assertThat(report.at("/domains")).hasSize(4);
        assertThat(report.at("/questions")).hasSize(8);
        assertThat(report.at("/questions/0/explanationFr").asText()).isEqualTo(SECRET_EXPLANATION);
        assertThat(report.at("/questions/0/correctAnswer/selected/0").asText()).isEqualTo("A");

        // la remise est idempotente et le rapport reste consultable
        mockMvc.perform(post(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/submit").with(as("awa@test.local"))).andExpect(status().isOk());
        mockMvc.perform(get(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/report").with(as("awa@test.local"))).andExpect(status().isOk());
        // plus de modification après la remise
        String ref = attempt.at("/questions/0/ref").asText();
        mockMvc.perform(put(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/answers/" + ref).with(as("awa@test.local"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"B\"]}}")).andExpect(status().isConflict());
    }

    @Test
    void aWrongOrEmptyExam_failsAgainstThePassScore() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 3);
        JsonNode attempt = start("awa@test.local", "EXAM");
        answerAll("awa@test.local", attempt, "B");
        JsonNode report = json(mockMvc.perform(post(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/submit").with(as("awa@test.local")))).at("/data");
        assertThat(report.at("/summary/scaledScore").asInt()).isZero();
        assertThat(report.at("/summary/passed").asBoolean()).isFalse();
    }

    @Test
    void whenTheTimeIsUp_theExamIsSubmittedAutomatically_andNoMoreAnswersAreAccepted() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 3);
        JsonNode attempt = start("awa@test.local", "EXAM");
        String id = attempt.at("/id").asText();
        String first = attempt.at("/questions/0/ref").asText();
        mockMvc.perform(put(LEARNER + "/attempts/" + id + "/answers/" + first).with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":{\"selected\":[\"A\"]}}")).andExpect(status().isOk());

        // le temps est écoulé (échéance reculée directement en base)
        sync();
        jdbc.update("UPDATE sim_attempt SET deadline_at = NOW() - INTERVAL '10 minutes' WHERE id = ?::uuid", id);
        sync();
        mockMvc.perform(put(LEARNER + "/attempts/" + id + "/answers/" + attempt.at("/questions/1/ref").asText()).with(as("awa@test.local"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"A\"]}}")).andExpect(status().isConflict());

        JsonNode report = json(mockMvc.perform(get(LEARNER + "/attempts/" + id + "/report").with(as("awa@test.local"))).andExpect(status().isOk())).at("/data");
        assertThat(report.at("/summary/status").asText()).isEqualTo("EXPIRED");
        assertThat(report.at("/summary/scorePercent").asDouble()).isEqualTo(12.5);   // 1 bonne réponse sur 8 enregistrée avant l'échéance
        assertThat(jdbc.queryForList("SELECT type FROM sim_attempt_event WHERE attempt_id = ?::uuid", id)).extracting(r -> r.get("type")).contains("START", "EXPIRE");
    }

    @Test
    void theSweepSubmitsOverdueExamsWithoutAnyRequest() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 3);
        String id = start("awa@test.local", "EXAM").at("/id").asText();
        sync();
        jdbc.update("UPDATE sim_attempt SET deadline_at = NOW() - INTERVAL '1 hour' WHERE id = ?::uuid", id);
        sync();
        attemptService.expireOverdue();
        sync();
        assertThat(jdbc.queryForObject("SELECT status FROM sim_attempt WHERE id = ?::uuid", String.class, id)).isEqualTo("EXPIRED");
    }

    @Autowired private com.modeltech.datamasteryhub.modules.simulator.service.AttemptService attemptService;

    // ── Quota ────────────────────────────────────────────────────────

    @Test
    void theExamQuotaIsEnforced_andAnAbandonedExamStillCounts() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 1);
        JsonNode first = start("awa@test.local", "EXAM");
        mockMvc.perform(post(LEARNER + "/attempts/" + first.at("/id").asText() + "/submit").with(as("awa@test.local"))).andExpect(status().isOk());

        mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\"}"))
                .andExpect(status().isConflict());
        // l'étude reste illimitée
        mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"STUDY\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT exam_attempts_used FROM sim_access_grant", Integer.class)).isEqualTo(1);
    }

    // ── Étude ────────────────────────────────────────────────────────

    @Test
    void studyMode_givesImmediateCorrection_forTheChosenDomain() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 0);
        JsonNode attempt = json(mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"STUDY\",\"domain\":\"SEC\",\"count\":3}")).andExpect(status().isCreated())).at("/data");
        assertThat(attempt.at("/totalQuestions").asInt()).isEqualTo(3);
        assertThat(attempt.at("/deadlineAt").isNull()).isTrue();
        assertNoAnswerKeys(attempt);

        JsonNode feedback = json(mockMvc.perform(put(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/answers/" + attempt.at("/questions/0/ref").asText())
                        .with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"B\"]}}"))
                .andExpect(status().isOk())).at("/data/feedback");
        assertThat(feedback.at("/score").asDouble()).isZero();
        assertThat(feedback.at("/correctAnswer/selected/0").asText()).isEqualTo("A");
        assertThat(feedback.at("/explanationFr").asText()).isEqualTo(SECRET_EXPLANATION);

        mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"STUDY\",\"domain\":\"ZZZ\"}")).andExpect(status().isConflict());
    }

    @Test
    void anExamNeedsEnoughPublishedQuestions_andDoesNotBurnAnAttemptWhenItFails() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 1);
        sync();
        jdbc.update("UPDATE sim_item_version SET status = 'RETIRED', retired_at = NOW(), retire_reason = 'test' WHERE status = 'PUBLISHED'");
        sync();
        mockMvc.perform(post(LEARNER + "/attempts").with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT exam_attempts_used FROM sim_access_grant", Integer.class)).isZero();
    }

    // ── Isolation entre apprenants (IDOR) ────────────────────────────

    @Test
    void anotherLearnerCannotReadOrTouchSomeoneElsesAttempt() throws Exception {
        learner("awa@test.local");
        learner("moussa@test.local");
        grant("awa@test.local", 30, 2);
        grant("moussa@test.local", 30, 2);
        JsonNode attempt = start("awa@test.local", "EXAM");
        String id = attempt.at("/id").asText();
        String ref = attempt.at("/questions/0/ref").asText();
        RequestPostProcessor other = as("moussa@test.local");

        mockMvc.perform(get(LEARNER + "/attempts/" + id).with(other)).andExpect(status().isNotFound());
        mockMvc.perform(put(LEARNER + "/attempts/" + id + "/answers/" + ref).with(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":{\"selected\":[\"A\"]}}")).andExpect(status().isNotFound());
        mockMvc.perform(post(LEARNER + "/attempts/" + id + "/submit").with(other)).andExpect(status().isNotFound());
        mockMvc.perform(get(LEARNER + "/attempts/" + id + "/report").with(other)).andExpect(status().isNotFound());
        // l'historique de l'autre apprenant est vide
        assertThat(json(mockMvc.perform(get(LEARNER + "/attempts").with(other))).at("/data")).isEmpty();
        // et la tentative d'Awa n'a pas bougé
        assertThat(json(mockMvc.perform(get(LEARNER + "/attempts/" + id).with(as("awa@test.local")))).at("/data/answered").asInt()).isZero();
    }

    @Test
    void invalidAnswersAreRefused_andAnonymousCallsAreRejected() throws Exception {
        learner("awa@test.local");
        grant("awa@test.local", 30, 1);
        JsonNode attempt = start("awa@test.local", "EXAM");
        String path = LEARNER + "/attempts/" + attempt.at("/id").asText() + "/answers/" + attempt.at("/questions/0/ref").asText();
        mockMvc.perform(put(path).with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"Z\"]}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(path).with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"A\",\"B\"]}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(LEARNER + "/attempts/" + attempt.at("/id").asText() + "/answers/" + UUID.randomUUID()).with(as("awa@test.local"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"selected\":[\"A\"]}}")).andExpect(status().isNotFound());
        mockMvc.perform(get(LEARNER + "/catalog")).andExpect(status().isUnauthorized());
    }

    /** Les écritures JPA sont envoyées à la base et le contexte est vidé : le SQL direct et le code testé voient le même état. */
    private void sync() {
        em.flush();
        em.clear();
    }

    // ── Utilitaires ──────────────────────────────────────────────────

    /** Échoue si le moindre champ de correction se trouve dans une vue de tentative en cours. */
    private static void assertNoAnswerKeys(JsonNode node) {
        Set<String> forbidden = Set.of("correct", "correctAnswer", "explanationFr", "explanation");
        node.fieldNames().forEachRemaining(name -> assertThat(forbidden).as("champ interdit « " + name + " »").doesNotContain(name));
        node.forEach(AttemptEngineIT::assertNoAnswerKeys);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
