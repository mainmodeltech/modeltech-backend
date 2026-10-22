package com.modeltech.datamasteryhub.modules.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.notification.service.ProgramProposalNotice;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Propositions de modification du programme : portée du formateur, rien d'existant ne disparaît, fusion à la validation. */
class ProgramProposalIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/program-proposals";
    private static final String TRAINER = "formateur@test.local";
    private static final String REVIEWER = "peda@test.local";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private RoleRepository roleRepository;

    private Bootcamp bootcamp;
    private Bootcamp other;
    private AdminUser trainer;
    private String moduleId;
    private String videoId;
    private String resourceId;
    private String quizId;
    private String questionId;

    @BeforeEach
    void setUp() throws Exception {
        trainer = account(TRAINER, "Moussa Fall", "ROLE_TRAINER");
        account(REVIEWER, "Awa Pédagogie", "ROLE_PEDAGOGY");
        bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        other = bootcampRepository.save(TestData.bootcamp("excel", "Excel", null));
        assign(bootcamp, trainer);

        JsonNode live = json(mockMvc.perform(put(contentUrl()).with(TestAuth.as("admin@test.local", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"modules":[{"title":"Module 1","lessons":[
                          {"title":"1.1 Vidéo","type":"VIDEO","status":"PUBLISHED","resources":[{"name":"Support","fileType":"pdf"}]},
                          {"title":"1.2 Quiz","type":"QUIZ","status":"PUBLISHED","quiz":{"questionCount":1,"passThreshold":50}}]}]}"""))
                .andExpect(status().isOk()));
        moduleId = live.at("/modules/0/id").asText();
        videoId = live.at("/modules/0/lessons/0/id").asText();
        resourceId = live.at("/modules/0/lessons/0/resources/0/id").asText();
        quizId = live.at("/modules/0/lessons/1/id").asText();
        JsonNode bank = json(mockMvc.perform(put("/api/v1/admin/lessons/" + quizId + "/quiz").with(TestAuth.as("admin@test.local", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"questions\":[" + question(null, "Question en ligne") + "]}"))
                .andExpect(status().isOk()));
        questionId = bank.at("/questions/0/id").asText();
    }

    // ── Portée ───────────────────────────────────────────────────────

    @Test
    void trainer_proposesOnlyForFormationsOfHisSessions() throws Exception {
        JsonNode formations = json(mockMvc.perform(get(BASE + "/mine/formations").with(trainer())).andExpect(status().isOk()));
        assertThat(formations.size()).isEqualTo(1);
        assertThat(formations.at("/0/formationTitle").asText()).isEqualTo("Power BI");
        assertThat(formations.at("/0/proposal").isNull()).isTrue();

        mockMvc.perform(get(BASE + "/mine/" + other.getId()).with(trainer())).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/mine/" + other.getId()).with(trainer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":{\"modules\":[]},\"quizBanks\":[]}")).andExpect(status().isForbidden());
        // La relecture est réservée à la pédagogie ; un partenaire (programme, mais pas toutes les formations) non plus
        mockMvc.perform(get(BASE).with(trainer())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE).with(TestAuth.as("partenaire@test.local", "PARTNER"))).andExpect(status().isForbidden());

        // Sans proposition : copie du programme en ligne et de ses banques
        mockMvc.perform(get(BASE + "/mine/" + bootcamp.getId()).with(trainer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isEmpty())
                .andExpect(jsonPath("$.status").isEmpty())
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.content.modules[0].lessons.length()").value(2))
                .andExpect(jsonPath("$.quizBanks[0].lessonRef").value(quizId))
                .andExpect(jsonPath("$.quizBanks[0].questions[0].id").value(questionId))
                .andExpect(jsonPath("$.live").doesNotExist());
    }

    // ── Rien d'existant ne disparaît ─────────────────────────────────

    @Test
    void removingAnExistingElement_isRefused() throws Exception {
        ObjectNode body = startingPoint();
        lessons(body).remove(0);
        save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                "La leçon « 1.1 Vidéo » existe en ligne et ne peut pas être retirée : passez-la en brouillon."));

        body = startingPoint();
        ((ArrayNode) lessons(body).get(0).get("resources")).remove(0);
        save(body).andExpect(status().isBadRequest());

        body = startingPoint();
        ((ArrayNode) body.at("/quizBanks/0/questions")).remove(0);
        save(body).andExpect(status().isBadRequest());

        body = startingPoint();
        ((ArrayNode) body.at("/content/modules")).remove(0);
        save(body).andExpect(status().isBadRequest());

        // Passer une leçon en brouillon est permis ; un autre statut que celui en ligne, non
        body = startingPoint();
        ((ObjectNode) lessons(body).get(0)).put("status", "DRAFT");
        save(body).andExpect(status().isOk());
        ((ObjectNode) lessons(body).get(0)).put("status", "SCHEDULED");
        save(body).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownId_isRefused() throws Exception {
        ObjectNode body = startingPoint();
        ((ObjectNode) lessons(body).get(0)).put("id", java.util.UUID.randomUUID().toString());
        save(body).andExpect(status().isBadRequest());

        body = startingPoint();
        ((ObjectNode) body.at("/quizBanks/0/questions/0")).put("id", java.util.UUID.randomUUID().toString());
        save(body).andExpect(status().isBadRequest());
    }

    // ── Fusion ───────────────────────────────────────────────────────

    @Test
    void approval_mergesWithoutLosingWhatWasAddedOnlineMeanwhile() throws Exception {
        ObjectNode body = startingPoint();
        ((ObjectNode) body.at("/content")).putObject("settings").put("sequentialUnlock", false);   // ignoré
        ((ObjectNode) lessons(body).get(0)).put("title", "1.1 Vidéo (revue)");
        lessons(body).add(objectMapper.readTree("""
                {"title":"1.3 Nouvelle vidéo","type":"VIDEO","status":"PUBLISHED"}"""));
        lessons(body).add(objectMapper.readTree("""
                {"clientKey":"ck-quiz","title":"1.4 Nouveau quiz","type":"QUIZ","status":"DRAFT","quiz":{"questionCount":1,"passThreshold":60}}"""));
        ((ArrayNode) body.at("/quizBanks/0/questions")).add(question(null, "Question ajoutée"));
        ((ArrayNode) body.get("quizBanks")).add(objectMapper.createObjectNode().put("lessonRef", "ck-quiz")
                .set("questions", objectMapper.createArrayNode().add(question(null, "Question du nouveau quiz"))));

        JsonNode saved = json(save(body).andExpect(status().isOk()));
        assertThat(saved.get("status").asText()).isEqualTo("DRAFT");
        assertThat(saved.at("/content/modules/0/lessons/2/status").asText()).isEqualTo("DRAFT");   // nouvelle leçon : brouillon
        assertThat(saved.at("/content/modules/0/lessons/2/clientKey").asText()).isNotBlank();       // généré
        assertThat(saved.at("/content/settings/sequentialUnlock").asBoolean()).isTrue();            // celui en ligne
        String proposalId = saved.get("id").asText();
        mockMvc.perform(post(BASE + "/mine/" + bootcamp.getId() + "/submit").with(trainer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        verify(notificationService).notifyProgramProposalSubmitted(any(ProgramProposalNotice.class));

        // Pendant la relecture, la pédagogie ajoute en ligne une leçon et un module
        JsonNode live = json(mockMvc.perform(get(contentUrl()).with(reviewer())).andExpect(status().isOk()));
        ArrayNode liveLessons = (ArrayNode) live.at("/modules/0/lessons");
        liveLessons.add(objectMapper.readTree("{\"title\":\"1.5 Ajoutée en ligne\",\"type\":\"VIDEO\",\"status\":\"PUBLISHED\"}"));
        ((ArrayNode) live.get("modules")).add(objectMapper.readTree("{\"title\":\"Module 2 en ligne\",\"lessons\":[]}"));
        mockMvc.perform(put(contentUrl()).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(live.toString()))
                .andExpect(status().isOk());

        JsonNode detail = json(mockMvc.perform(get(BASE + "/" + proposalId).with(reviewer())).andExpect(status().isOk()));
        assertThat(detail.get("stale").asBoolean()).isTrue();
        assertThat(detail.at("/live/content/modules").size()).isEqualTo(2);
        mockMvc.perform(get(BASE + "/pending-count").with(reviewer())).andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post(BASE + "/" + proposalId + "/approve").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Merci\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewerName").value("Awa Pédagogie"));

        JsonNode merged = json(mockMvc.perform(get(contentUrl()).with(reviewer())).andExpect(status().isOk()));
        assertThat(titles(merged.at("/modules/0/lessons"))).containsExactly(
                "1.1 Vidéo (revue)", "1.2 Quiz", "1.3 Nouvelle vidéo", "1.4 Nouveau quiz", "1.5 Ajoutée en ligne");
        assertThat(merged.at("/modules/0/lessons/0/id").asText()).isEqualTo(videoId);
        assertThat(merged.at("/modules/0/lessons/0/resources/0/id").asText()).isEqualTo(resourceId);
        assertThat(merged.at("/modules/0/lessons/2/status").asText()).isEqualTo("DRAFT");
        assertThat(merged.at("/modules/0/lessons/4/status").asText()).isEqualTo("PUBLISHED");
        assertThat(merged.at("/modules/1/title").asText()).isEqualTo("Module 2 en ligne");
        assertThat(merged.at("/settings/sequentialUnlock").asBoolean()).isTrue();

        JsonNode quizBank = json(mockMvc.perform(get("/api/v1/admin/lessons/" + quizId + "/quiz").with(reviewer())).andExpect(status().isOk()));
        assertThat(quizBank.get("questions").size()).isEqualTo(2);
        assertThat(quizBank.at("/questions/0/id").asText()).isEqualTo(questionId);
        String newQuizId = merged.at("/modules/0/lessons/3/id").asText();
        JsonNode newBank = json(mockMvc.perform(get("/api/v1/admin/lessons/" + newQuizId + "/quiz").with(reviewer())).andExpect(status().isOk()));
        assertThat(newBank.at("/questions/0/text").asText()).isEqualTo("Question du nouveau quiz");

        verify(notificationService).sendProgramProposalDecision(any(ProgramProposalNotice.class));
        entityManager.flush();   // le journal est écrit par JPA dans la transaction du test
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action LIKE 'PROGRAM_PROPOSAL_%'", Integer.class)).isEqualTo(3);
        // Plus de proposition en cours : le formateur repart du programme en ligne
        mockMvc.perform(get(BASE + "/mine/" + bootcamp.getId()).with(trainer())).andExpect(jsonPath("$.id").isEmpty());
    }

    @Test
    void temporaryIds_areKeptAsNewElements_andThePayloadTellsOwnershipAndLiveStatuses() throws Exception {
        ObjectNode body = startingPoint();
        ((ArrayNode) body.at("/content/modules")).add(objectMapper.readTree("""
                {"id":"new-mod-1","title":"Module nouveau","lessons":[
                  {"id":"new-les-1","title":"Leçon nouvelle","type":"VIDEO","status":"DRAFT",
                   "resources":[{"id":"new-res-1","name":"Fiche","fileType":"pdf"}]}]}"""));
        ((ObjectNode) lessons(body).get(0)).put("status", "DRAFT");
        JsonNode saved = json(save(body).andExpect(status().isOk()));
        assertThat(saved.at("/content/modules/1/id").asText()).isEqualTo("new-mod-1");
        assertThat(saved.at("/content/modules/1/lessons/0/id").asText()).isEqualTo("new-les-1");
        assertThat(saved.at("/content/modules/1/lessons/0/clientKey").asText()).isEqualTo("new-les-1");
        assertThat(saved.at("/content/modules/1/lessons/0/resources/0/id").asText()).isEqualTo("new-res-1");
        assertThat(saved.get("own").asBoolean()).isTrue();
        assertThat(saved.at("/liveLessonStatuses/" + videoId).asText()).isEqualTo("PUBLISHED");   // pour revenir du brouillon
        assertThat(saved.at("/liveLessonStatuses").size()).isEqualTo(2);

        String id = saved.get("id").asText();
        mockMvc.perform(post(BASE + "/mine/" + bootcamp.getId() + "/submit").with(trainer())).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + id).with(reviewer())).andExpect(jsonPath("$.own").value(false));
        mockMvc.perform(post(BASE + "/" + id + "/approve").with(reviewer())).andExpect(status().isOk());

        JsonNode merged = json(mockMvc.perform(get(contentUrl()).with(reviewer())).andExpect(status().isOk()));
        assertThat(merged.at("/modules/1/title").asText()).isEqualTo("Module nouveau");
        assertThat(merged.at("/modules/1/id").asText()).matches("[0-9a-f-]{36}");
        assertThat(merged.at("/modules/1/lessons/0/resources/0/name").asText()).isEqualTo("Fiche");
        assertThat(merged.at("/modules/0/lessons/0/status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void aDraftStartedEarlier_doesNotHaveToContainWhatWasAddedOnlineSince() throws Exception {
        ObjectNode body = startingPoint();
        save(body).andExpect(status().isOk());
        JsonNode live = json(mockMvc.perform(get(contentUrl()).with(reviewer())));
        ((ArrayNode) live.at("/modules/0/lessons")).add(objectMapper.readTree("{\"title\":\"Ajoutée en ligne\",\"type\":\"VIDEO\",\"status\":\"PUBLISHED\"}"));
        mockMvc.perform(put(contentUrl()).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(live.toString()))
                .andExpect(status().isOk());

        // Le formateur réenregistre son brouillon (sans la leçon ajoutée en ligne) : permis, la fusion la conservera
        save(body).andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(true));
        // Ce qu'il connaissait reste exigé
        lessons(body).remove(0);
        save(body).andExpect(status().isBadRequest());
    }

    @Test
    void approval_canPublishTheNewLessons() throws Exception {
        ObjectNode body = startingPoint();
        lessons(body).add(objectMapper.readTree("{\"title\":\"1.3 Nouvelle\",\"type\":\"VIDEO\",\"status\":\"DRAFT\"}"));
        String id = json(save(body).andExpect(status().isOk())).get("id").asText();
        mockMvc.perform(post(BASE + "/mine/" + bootcamp.getId() + "/submit").with(trainer())).andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/" + id + "/approve").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"publishNewLessons\":true}")).andExpect(status().isOk());
        JsonNode merged = json(mockMvc.perform(get(contentUrl()).with(reviewer())).andExpect(status().isOk()));
        assertThat(merged.at("/modules/0/lessons/2/title").asText()).isEqualTo("1.3 Nouvelle");
        assertThat(merged.at("/modules/0/lessons/2/status").asText()).isEqualTo("PUBLISHED");
    }

    // ── Séparation des tâches et états ───────────────────────────────

    @Test
    void theAuthorCannotApproveHisOwnProposal() throws Exception {
        AdminUser both = account("both@test.local", "Gestion complète", "ROLE_ADMIN");   // propose ET relit
        assign(bootcamp, both);
        RequestPostProcessor me = TestAuth.as("both@test.local", "ADMIN");
        String id = json(mockMvc.perform(put(BASE + "/mine/" + bootcamp.getId()).with(me).contentType(MediaType.APPLICATION_JSON)
                .content(startingPoint().toString())).andExpect(status().isOk())).get("id").asText();
        mockMvc.perform(post(BASE + "/mine/" + bootcamp.getId() + "/submit").with(me)).andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/" + id + "/approve").with(me)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + id + "/reject").with(me).contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"non\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/pending-count").with(me)).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void stateTransitions_areEnforced() throws Exception {
        String formation = BASE + "/mine/" + bootcamp.getId();
        mockMvc.perform(post(formation + "/submit").with(trainer())).andExpect(status().isConflict());   // rien à soumettre
        String id = json(save(startingPoint()).andExpect(status().isOk())).get("id").asText();
        mockMvc.perform(post(formation + "/withdraw").with(trainer())).andExpect(status().isConflict());
        mockMvc.perform(post(BASE + "/" + id + "/approve").with(reviewer())).andExpect(status().isConflict());

        mockMvc.perform(post(formation + "/submit").with(trainer())).andExpect(status().isOk());
        mockMvc.perform(post(formation + "/submit").with(trainer())).andExpect(status().isConflict());
        save(startingPoint()).andExpect(status().isConflict());   // en relecture : plus modifiable

        mockMvc.perform(post(formation + "/withdraw").with(trainer())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(post(formation + "/submit").with(trainer())).andExpect(status().isOk());

        // Demande de modifications : commentaire obligatoire, puis le formateur corrige et soumet de nouveau
        mockMvc.perform(post(BASE + "/" + id + "/request-changes").with(reviewer())).andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/" + id + "/request-changes").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Précisez le quiz\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.reviewComment").value("Précisez le quiz"));
        save(startingPoint()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"));
        mockMvc.perform(post(formation + "/submit").with(trainer())).andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/" + id + "/reject").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Hors sujet\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post(BASE + "/" + id + "/approve").with(reviewer())).andExpect(status().isConflict());

        JsonNode formations = json(mockMvc.perform(get(formation.replace(bootcamp.getId().toString(), "formations")).with(trainer())));
        assertThat(formations.at("/0/proposal/status").asText()).isEqualTo("REJECTED");
        JsonNode list = json(mockMvc.perform(get(BASE).param("status", "REJECTED").with(reviewer())).andExpect(status().isOk()));
        assertThat(list.at("/0/authorName").asText()).isEqualTo("Moussa Fall");
    }

    // ── utilitaires ───────────────────────────────────────────────────

    /** {content, quizBanks} tel que le formateur le reçoit au départ (copie du programme en ligne). */
    private ObjectNode startingPoint() throws Exception {
        JsonNode mine = json(mockMvc.perform(get(BASE + "/mine/" + bootcamp.getId()).with(trainer())).andExpect(status().isOk()));
        ObjectNode body = objectMapper.createObjectNode();
        body.set("content", mine.get("content").deepCopy());
        body.set("quizBanks", mine.get("quizBanks").deepCopy());
        return body;
    }

    private ArrayNode lessons(ObjectNode body) {
        return (ArrayNode) body.at("/content/modules/0/lessons");
    }

    private ObjectNode question(String id, String text) {
        ObjectNode q = objectMapper.createObjectNode();
        if (id != null) q.put("id", id);
        q.put("text", text);
        ArrayNode choices = q.putArray("choices");
        choices.addObject().put("label", "Oui").put("correct", true);
        choices.addObject().put("label", "Non").put("correct", false);
        return q;
    }

    private ResultActions save(ObjectNode body) throws Exception {
        return mockMvc.perform(put(BASE + "/mine/" + bootcamp.getId()).with(trainer())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private void assign(Bootcamp b, AdminUser who) {
        BootcampSession s = TestData.session(b, "Cohorte " + who.getEmail(), LocalDate.now(), SessionStatus.UPCOMING);
        s.setTrainer(who);
        sessionRepository.save(s);
    }

    private AdminUser account(String email, String name, String role) {
        AdminUser a = new AdminUser();
        a.setEmail(email);
        a.setFullName(name);
        a.setPasswordHash("x");
        a.setRoles(new HashSet<>(List.of(roleRepository.findByName(role).orElseThrow())));
        return adminUserRepository.save(a);
    }

    private String contentUrl() {
        return "/api/v1/admin/formations/" + bootcamp.getId() + "/content";
    }

    private List<String> titles(JsonNode lessons) {
        List<String> titles = new ArrayList<>();
        lessons.forEach(l -> titles.add(l.get("title").asText()));
        return titles;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor trainer() {
        return TestAuth.as(TRAINER, "TRAINER");
    }

    private static RequestPostProcessor reviewer() {
        return TestAuth.as(REVIEWER, "PEDAGOGY");
    }
}
