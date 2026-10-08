package com.modeltech.datamasteryhub.modules.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.exception.StorageException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Banque de questions, tentatives de quiz, projet final, appel des lives et suivi de session. */
class EvaluationFlowIT extends AbstractIntegrationTest {

    @MockBean private StorageService storageService;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LearnerRepository learnerRepository;

    private Bootcamp bootcamp;
    private BootcampSession session;
    private String videoId;
    private String quizId;
    private String liveId;
    private String lateVideoId;
    private final RequestPostProcessor awa = user("awa@example.com", "LEARNER");

    @BeforeEach
    void setUp() throws Exception {
        bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().minusDays(7), SessionStatus.IN_PROGRESS);
        session.setEndDate(LocalDate.now().plusDays(60));
        session = sessionRepository.save(session);

        JsonNode programme = json(mockMvc.perform(put(contentUrl()).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modules":[
                                  {"title":"Module 1","lessons":[
                                    {"title":"1.1 Intro","type":"VIDEO","status":"PUBLISHED"},
                                    {"title":"1.2 Quiz","type":"QUIZ","status":"PUBLISHED","quiz":{"questionCount":2,"passThreshold":50,"maxAttempts":2}},
                                    {"title":"1.3 Live","type":"LIVE","status":"PUBLISHED","liveAt":"2026-01-10T18:00"}]},
                                  {"title":"Module 2","lessons":[{"title":"2.1 Suite","type":"VIDEO","status":"PUBLISHED"}]}]}"""))
                .andExpect(status().isOk()));
        videoId = programme.at("/modules/0/lessons/0/id").asText();
        quizId = programme.at("/modules/0/lessons/1/id").asText();
        liveId = programme.at("/modules/0/lessons/2/id").asText();
        lateVideoId = programme.at("/modules/1/lessons/0/id").asText();

        enroll("awa@example.com");
    }

    // ── Banque de questions ──────────────────────────────────────────

    @Test
    void quizBank_isEditedAsATree_andValidated() throws Exception {
        JsonNode bank = saveBank(threeQuestions());
        assertThat(bank.get("questions").size()).isEqualTo(3);
        assertThat(bank.get("questionCount").asInt()).isEqualTo(2);
        assertThat(bank.at("/questions/0/choices/0/correct").asBoolean()).isTrue();

        // retrait d'une question et correction d'une réponse (ids conservés)
        String q1 = bank.at("/questions/0/id").asText();
        String q2 = bank.at("/questions/1/id").asText();
        JsonNode next = saveBank("""
                {"questions":[
                  {"id":"%s","text":"Q1 corrigée","choices":[{"id":"%s","label":"A","correct":true},{"label":"B"}]},
                  {"id":"%s","text":"Q2","explanation":"parce que","choices":[{"label":"X"},{"label":"Y","correct":true}]}]}"""
                .formatted(q1, bank.at("/questions/0/choices/0/id").asText(), q2));
        assertThat(next.get("questions").size()).isEqualTo(2);
        assertThat(next.at("/questions/0/text").asText()).isEqualTo("Q1 corrigée");
        assertThat(next.at("/questions/0/choices/0/id").asText()).isEqualTo(bank.at("/questions/0/choices/0/id").asText());

        String[] invalid = {
                "{\"questions\":[{\"text\":\"Sans bonne réponse\",\"choices\":[{\"label\":\"A\"},{\"label\":\"B\"}]}]}",
                "{\"questions\":[{\"text\":\"Deux bonnes\",\"choices\":[{\"label\":\"A\",\"correct\":true},{\"label\":\"B\",\"correct\":true}]}]}",
                "{\"questions\":[{\"text\":\"Une seule réponse\",\"choices\":[{\"label\":\"A\",\"correct\":true}]}]}",
                "{\"questions\":[{\"text\":\"\",\"choices\":[{\"label\":\"A\",\"correct\":true},{\"label\":\"B\"}]}]}",
                "{\"questions\":[{\"text\":\"Réponse vide\",\"choices\":[{\"label\":\"A\",\"correct\":true},{\"label\":\" \"}]}]}",
        };
        for (String body : invalid) {
            mockMvc.perform(put(bankUrl(quizId)).with(staff("EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        // une leçon qui n'est pas un quiz n'a pas de banque
        mockMvc.perform(get(bankUrl(videoId)).with(staff("EDITOR"))).andExpect(status().isBadRequest());
        mockMvc.perform(get(bankUrl(UUID.randomUUID().toString())).with(staff("EDITOR"))).andExpect(status().isNotFound());
        // réservé au contenu pédagogique
        mockMvc.perform(get(bankUrl(quizId)).with(staff("TRAINER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(bankUrl(quizId)).with(staff("LEARNER"))).andExpect(status().isForbidden());
    }

    // ── Passage d'un quiz ────────────────────────────────────────────

    @Test
    void attempt_neverRevealsTheAnswers_resumesAnOpenAttempt_andDrawsTheConfiguredCount() throws Exception {
        saveBank(threeQuestions());

        String body = mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode start = objectMapper.readTree(body);

        assertThat(body).doesNotContain("correct").doesNotContain("explanation");
        assertThat(start.get("questions").size()).isEqualTo(2);                 // questionCount = 2 sur 3 en banque
        assertThat(start.get("attemptNumber").asInt()).isEqualTo(1);
        assertThat(start.get("maxAttempts").asInt()).isEqualTo(2);
        assertThat(start.get("passThreshold").asInt()).isEqualTo(50);

        // recharger la page ne brûle pas de tentative
        JsonNode again = json(mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isOk()));
        assertThat(again.get("attemptId").asText()).isEqualTo(start.get("attemptId").asText());
        assertThat(again.at("/questions/0/id").asText()).isEqualTo(start.at("/questions/0/id").asText());
    }

    @Test
    void failThenPass_marksTheLessonCompleted_andUnlocksTheNextModule() throws Exception {
        JsonNode bank = saveBank(threeQuestions());

        // 1re tentative : tout faux → pas de correction tant qu'il reste une tentative
        JsonNode first = json(mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isOk()));
        JsonNode failed = submit(first, bank, false);
        assertThat(failed.get("passed").asBoolean()).isFalse();
        assertThat(failed.get("score").asInt()).isZero();
        assertThat(failed.get("attemptsRemaining").asInt()).isEqualTo(1);
        assertThat(failed.get("review").isNull()).isTrue();

        JsonNode overview = json(mockMvc.perform(get(overviewUrl()).with(awa)).andExpect(status().isOk()));
        assertThat(overview.at("/quizzes/0/status").asText()).isEqualTo("AVAILABLE");
        assertThat(overview.at("/quizzes/0/attemptsUsed").asInt()).isEqualTo(1);
        assertThat(overview.at("/quizzes/0/bestScore").asInt()).isZero();

        // le module 2 reste fermé tant que le quiz n'est pas réussi
        complete(videoId).andExpect(status().isNoContent());
        complete(lateVideoId).andExpect(status().isForbidden());

        // 2e tentative : tout juste → réussite, correction fournie, leçon terminée
        JsonNode second = json(mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isOk()));
        assertThat(second.get("attemptNumber").asInt()).isEqualTo(2);
        JsonNode passed = submit(second, bank, true);
        assertThat(passed.get("passed").asBoolean()).isTrue();
        assertThat(passed.get("score").asInt()).isEqualTo(100);
        assertThat(passed.get("review").size()).isEqualTo(2);
        assertThat(passed.at("/review/0/correctChoiceId").asText()).isNotBlank();

        JsonNode course = json(mockMvc.perform(get("/api/v1/learner/formations/" + bootcamp.getId() + "/course").with(awa))
                .andExpect(status().isOk()));
        assertThat(course.get("progress").toString()).contains(quizId);
        complete(lateVideoId).andExpect(status().isForbidden());                // le live du module 1 reste à faire
        complete(liveId).andExpect(status().isNoContent());
        complete(lateVideoId).andExpect(status().isNoContent());                // module 2 débloqué

        mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isConflict());   // déjà réussi
        overview = json(mockMvc.perform(get(overviewUrl()).with(awa)).andExpect(status().isOk()));
        assertThat(overview.at("/quizzes/0/status").asText()).isEqualTo("PASSED");
        assertThat(overview.at("/quizzes/0/bestScore").asInt()).isEqualTo(100);
    }

    @Test
    void exhaustedAttempts_revealTheCorrection_thenBlockNewAttempts() throws Exception {
        JsonNode bank = saveBank(threeQuestions());
        for (int i = 0; i < 2; i++) {
            JsonNode attempt = json(mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isOk()));
            JsonNode result = submit(attempt, bank, false);
            assertThat(result.get("review").isNull()).isEqualTo(i == 0);   // correction à la dernière tentative seulement
        }
        mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isConflict());
        JsonNode overview = json(mockMvc.perform(get(overviewUrl()).with(awa)).andExpect(status().isOk()));
        assertThat(overview.at("/quizzes/0/status").asText()).isEqualTo("FAILED");
    }

    @Test
    void submit_isOwnedByTheLearner_isFinal_andIgnoresForgedAnswers() throws Exception {
        JsonNode bank = saveBank(threeQuestions());
        JsonNode attempt = json(mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isOk()));
        String attemptId = attempt.get("attemptId").asText();

        learnerRepository.save(learner("intrus@example.com"));
        mockMvc.perform(post("/api/v1/learner/quiz-attempts/" + attemptId + "/submit")
                        .with(user("intrus@example.com", "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isNotFound());

        // réponses fantaisistes : ni question hors tirage, ni choix d'une autre question ne comptent
        String forged = "{\"answers\":{\"" + UUID.randomUUID() + "\":\"" + UUID.randomUUID() + "\"}}";
        mockMvc.perform(post("/api/v1/learner/quiz-attempts/" + attemptId + "/submit").with(awa)
                        .contentType(MediaType.APPLICATION_JSON).content(forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.correctCount").value(0));
        mockMvc.perform(post("/api/v1/learner/quiz-attempts/" + attemptId + "/submit").with(awa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{}}"))
                .andExpect(status().isConflict());
        assertThat(bank).isNotNull();
    }

    @Test
    void quizWithoutQuestions_isNotReady_andStrangersAreRefused() throws Exception {
        mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isConflict());

        learnerRepository.save(learner("sans-acces@example.com"));
        mockMvc.perform(post(startUrl()).with(user("sans-acces@example.com", "LEARNER"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/learner/quizzes/" + videoId + "/attempts").with(awa)).andExpect(status().isNotFound());
        mockMvc.perform(get(overviewUrl()).with(user("sans-acces@example.com", "LEARNER"))).andExpect(status().isForbidden());
    }

    // ── Projet final ─────────────────────────────────────────────────

    @Test
    void project_configurationIsValidated_andHiddenUntilItExists() throws Exception {
        mockMvc.perform(get(overviewUrl()).with(awa)).andExpect(jsonPath("$.project").doesNotExist());
        mockMvc.perform(get(projectUrl()).with(staff("EDITOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brief").doesNotExist());

        String[] invalid = {
                "{\"acceptedExtensions\":[\"pbix\"],\"maxSizeMb\":10}",                       // pas de consigne
                "{\"brief\":\"x\",\"acceptedExtensions\":[],\"maxSizeMb\":10}",               // aucune extension
                "{\"brief\":\"x\",\"acceptedExtensions\":[\"pb ix\"],\"maxSizeMb\":10}",      // extension invalide
                "{\"brief\":\"x\",\"acceptedExtensions\":[\"pbix\"],\"maxSizeMb\":0}",
                "{\"brief\":\"x\",\"acceptedExtensions\":[\"pbix\"],\"maxSizeMb\":500}",
        };
        for (String body : invalid) {
            mockMvc.perform(put(projectUrl()).with(staff("EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        defineProject();
        mockMvc.perform(get(overviewUrl()).with(awa))
                .andExpect(jsonPath("$.project.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.project.brief").value("Construire un tableau de bord complet."))
                .andExpect(jsonPath("$.project.acceptedExtensions[1]").value("xlsx"))
                .andExpect(jsonPath("$.conditions[?(@.key=='PROJECT')].met").value(false));
    }

    @Test
    void project_submissionLifecycle_uploadReviewLock() throws Exception {
        defineProject();
        when(storageService.uploadDocument(any(), anyString(), anySet(), anyLong()))
                .thenReturn(new StorageService.UploadResult("projects/x/a.pbix", null));
        when(storageService.presignedGetUrl(eq("projects/x/a.pbix"), anyInt())).thenReturn("https://minio.test/signed");
        MockMultipartFile file = new MockMultipartFile("file", "tableau.pbix", "application/octet-stream", new byte[2048]);
        String filesUrl = "/api/v1/learner/formations/" + bootcamp.getId() + "/project/files";

        JsonNode uploaded = json(mockMvc.perform(multipart(filesUrl).file(file).with(awa)).andExpect(status().isOk()));
        assertThat(uploaded.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(uploaded.at("/files/0/name").asText()).isEqualTo("tableau.pbix");
        assertThat(uploaded.at("/files/0/sizeLabel").asText()).isEqualTo("2 Ko");
        assertThat(uploaded.toString()).doesNotContain("projects/x");           // aucune clé de stockage côté apprenant

        String sessionPath = "/api/v1/admin/sessions/" + session.getId() + "/learners/" + learnerId("awa@example.com");
        mockMvc.perform(get(sessionPath + "/project/files").with(staff("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].downloadUrl").value("https://minio.test/signed"));

        // correction : motif obligatoire pour demander des corrections
        mockMvc.perform(post(sessionPath + "/project/review").with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CHANGES_REQUESTED\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(sessionPath + "/project/review").with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUBMITTED\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(sessionPath + "/project/review").with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CHANGES_REQUESTED\",\"message\":\"Ajoutez une page de synthèse.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.feedback[0].authorRole").value("Formateur"))
                .andExpect(jsonPath("$.feedback[0].message").value("Ajoutez une page de synthèse."));

        // l'apprenant corrige : le rendu repasse « à corriger »
        mockMvc.perform(multipart(filesUrl).file(file).with(awa))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(post(sessionPath + "/project/review").with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VALIDATED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"));

        // validé : plus modifiable
        mockMvc.perform(multipart(filesUrl).file(file).with(awa)).andExpect(status().isConflict());
        String fileId = uploaded.at("/files/0/id").asText();
        mockMvc.perform(delete(filesUrl + "/" + fileId).with(awa)).andExpect(status().isConflict());
        mockMvc.perform(get(overviewUrl()).with(awa))
                .andExpect(jsonPath("$.conditions[?(@.key=='PROJECT')].met").value(true));
    }

    @Test
    void project_deletingTheLastFileResetsTheSubmission_andStorageErrorsAreClientErrors() throws Exception {
        defineProject();
        when(storageService.uploadDocument(any(), anyString(), anySet(), anyLong()))
                .thenReturn(new StorageService.UploadResult("projects/x/b.pbix", null));
        String filesUrl = "/api/v1/learner/formations/" + bootcamp.getId() + "/project/files";
        MockMultipartFile file = new MockMultipartFile("file", "..\\..\\evil/rapport.pbix", "application/octet-stream", new byte[10]);

        JsonNode uploaded = json(mockMvc.perform(multipart(filesUrl).file(file).with(awa)).andExpect(status().isOk()));
        assertThat(uploaded.at("/files/0/name").asText()).isEqualTo("rapport.pbix");   // chemin retiré

        mockMvc.perform(delete(filesUrl + "/" + uploaded.at("/files/0/id").asText()).with(awa))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_STARTED"));
        verify(storageService).delete("projects/x/b.pbix");

        when(storageService.uploadDocument(any(), anyString(), anySet(), anyLong()))
                .thenThrow(new StorageException("Extension non autorisée : .exe"));
        mockMvc.perform(multipart(filesUrl).file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[5])).with(awa))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Extension non autorisée : .exe"));
    }

    // ── Appel des lives et suivi de session ──────────────────────────

    @Test
    void tracking_andAttendance() throws Exception {
        enroll("moussa@example.com");
        String awaId = learnerId("awa@example.com");
        String moussaId = learnerId("moussa@example.com");
        String base = "/api/v1/admin/sessions/" + session.getId();

        JsonNode before = json(mockMvc.perform(get(base + "/tracking").with(staff("TRAINER"))).andExpect(status().isOk()));
        assertThat(before.get("sessionName").asText()).isEqualTo("Cohorte 1");
        assertThat(before.get("deliveredBy").asText()).isEqualTo("Model Technologie");
        assertThat(before.get("statusLabel").asText()).isEqualTo("En cours");
        assertThat(before.get("formatLabel").asText()).isEqualTo("Présentiel");
        assertThat(before.get("capacity").asInt()).isEqualTo(20);
        assertThat(before.get("learners").size()).isEqualTo(2);
        assertThat(before.at("/learners/0/name").asText()).isEqualTo("Awa Diop");
        assertThat(before.at("/learners/0/project").asText()).isEqualTo("NOT_STARTED");
        assertThat(before.at("/learners/0/quizTotal").asInt()).isEqualTo(1);
        assertThat(before.at("/learners/0/certificate").asText()).isEqualTo("PENDING");
        assertThat(before.at("/lives/0/presentLearnerIds").isNull()).as("appel pas encore fait").isTrue();
        assertThat(before.at("/lives/0/status").asText()).isEqualTo("DONE");     // le live (2026-01-10) est passé

        // appel : Awa présente, Moussa absent
        mockMvc.perform(put(base + "/lives/" + liveId + "/attendance").with(staff("TRAINER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"presentLearnerIds\":[\"" + awaId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentLearnerIds[0]").value(awaId))
                .andExpect(jsonPath("$.presentLearnerIds.length()").value(1));
        // l'appel se corrige
        mockMvc.perform(put(base + "/lives/" + liveId + "/attendance").with(staff("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presentLearnerIds\":[\"" + awaId + "\",\"" + moussaId + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.presentLearnerIds.length()").value(2));

        JsonNode after = json(mockMvc.perform(get(base + "/tracking").with(staff("ADMIN"))).andExpect(status().isOk()));
        assertThat(after.at("/lives/0/presentLearnerIds").size()).isEqualTo(2);

        // refus : inconnu, hors session, live inexistant, rôle
        mockMvc.perform(put(base + "/lives/" + liveId + "/attendance").with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"presentLearnerIds\":[\"" + UUID.randomUUID() + "\"]}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(base + "/lives/" + liveId + "/attendance").with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"presentLearnerIds\":[\"pas-un-uuid\"]}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(base + "/lives/" + videoId + "/attendance").with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"presentLearnerIds\":[]}")).andExpect(status().isNotFound());
        mockMvc.perform(get(base + "/tracking").with(staff("EDITOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get(base + "/tracking").with(awa)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/sessions/" + UUID.randomUUID() + "/tracking").with(staff("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void certificate_isReadyOnlyWhenEveryConditionIsMet() throws Exception {
        JsonNode bank = saveBank(threeQuestions());
        String awaId = learnerId("awa@example.com");
        String base = "/api/v1/admin/sessions/" + session.getId();

        // leçons terminées, quiz réussi, présent au live : il ne manque que le projet final
        defineProject();
        complete(videoId).andExpect(status().isNoContent());
        JsonNode attempt = json(mockMvc.perform(post(startUrl()).with(awa)).andExpect(status().isOk()));
        submit(attempt, bank, true);
        complete(liveId).andExpect(status().isNoContent());
        complete(lateVideoId).andExpect(status().isNoContent());
        mockMvc.perform(put(base + "/lives/" + liveId + "/attendance").with(staff("TRAINER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"presentLearnerIds\":[\"" + awaId + "\"]}"))
                .andExpect(status().isOk());

        JsonNode overview = json(mockMvc.perform(get(overviewUrl()).with(awa)).andExpect(status().isOk()));
        List<String> unmet = new ArrayList<>();
        overview.get("conditions").forEach(c -> { if (!c.get("met").asBoolean()) unmet.add(c.get("key").asText()); });
        assertThat(unmet).containsExactly("PROJECT");
        assertThat(overview.at("/conditions/0/valueLabel").asText()).isEqualTo("4 / 4");

        JsonNode tracking = json(mockMvc.perform(get(base + "/tracking").with(staff("ADMIN"))).andExpect(status().isOk()));
        assertThat(tracking.at("/learners/0/certificate").asText()).isEqualTo("PENDING");
        assertThat(tracking.at("/learners/0/progressPercent").asInt()).isEqualTo(100);
        assertThat(tracking.at("/learners/0/quizPassed").asInt()).isEqualTo(1);

        // projet validé → certificat prêt
        when(storageService.uploadDocument(any(), anyString(), anySet(), anyLong()))
                .thenReturn(new StorageService.UploadResult("projects/x/c.pbix", null));
        mockMvc.perform(multipart("/api/v1/learner/formations/" + bootcamp.getId() + "/project/files")
                .file(new MockMultipartFile("file", "p.pbix", "application/octet-stream", new byte[5])).with(awa)).andExpect(status().isOk());
        mockMvc.perform(post(base + "/learners/" + awaId + "/project/review").with(staff("TRAINER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDATED\"}")).andExpect(status().isOk());
        tracking = json(mockMvc.perform(get(base + "/tracking").with(staff("ADMIN"))).andExpect(status().isOk()));
        assertThat(tracking.at("/learners/0/certificate").asText()).isEqualTo("READY");
    }

    // ── Données de test ──────────────────────────────────────────────

    private String threeQuestions() {
        return """
                {"questions":[
                  {"text":"Q1","explanation":"E1","choices":[{"label":"Bonne","correct":true},{"label":"Fausse 1"},{"label":"Fausse 2"}]},
                  {"text":"Q2","explanation":"E2","choices":[{"label":"Fausse"},{"label":"Bonne","correct":true}]},
                  {"text":"Q3","choices":[{"label":"Bonne","correct":true},{"label":"Fausse"}]}]}""";
    }

    private void defineProject() throws Exception {
        mockMvc.perform(put(projectUrl()).with(staff("EDITOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Construire un tableau de bord complet.\",\"deadlineLabel\":\"Avant le 30 novembre\","
                                + "\"acceptedExtensions\":[\".PBIX\",\"xlsx\",\"pbix\"],\"maxSizeMb\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedExtensions.length()").value(2))
                .andExpect(jsonPath("$.acceptedExtensions[0]").value("pbix"));
    }

    /** Soumet la tentative : toutes les bonnes réponses ({@code right}) ou toutes les mauvaises. */
    private JsonNode submit(JsonNode attempt, JsonNode bank, boolean right) throws Exception {
        StringBuilder answers = new StringBuilder();
        for (JsonNode q : attempt.get("questions")) {
            JsonNode source = null;
            for (JsonNode candidate : bank.get("questions")) {
                if (candidate.get("id").asText().equals(q.get("id").asText())) source = candidate;
            }
            assertThat(source).isNotNull();
            String choice = null;
            for (JsonNode c : source.get("choices")) {
                if (c.get("correct").asBoolean() == right) { choice = c.get("id").asText(); break; }
            }
            if (answers.length() > 0) answers.append(',');
            answers.append('"').append(q.get("id").asText()).append("\":\"").append(choice).append('"');
        }
        return json(mockMvc.perform(post("/api/v1/learner/quiz-attempts/" + attempt.get("attemptId").asText() + "/submit")
                        .with(awa).contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{" + answers + "}}"))
                .andExpect(status().isOk()));
    }

    private JsonNode saveBank(String body) throws Exception {
        return json(mockMvc.perform(put(bankUrl(quizId)).with(staff("EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()));
    }

    private void enroll(String email) {
        Learner learner = learnerRepository.save(learner(email));
        Registration reg = registrationRepository.save(TestData.registration(email, bootcamp, session, null));
        Enrollment e = new Enrollment();
        e.setLearner(learner);
        e.setRegistration(reg);
        e.setSession(session);
        e.setAccessStartsAt(session.getStartDate());
        e.setAccessEndsAt(session.getEndDate());
        enrollmentRepository.save(e);
    }

    private Learner learner(String email) {
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName(email.startsWith("moussa") ? "Moussa" : "Awa");
        l.setLastName("Diop");
        return l;
    }

    private String learnerId(String email) {
        return learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).orElseThrow().getId().toString();
    }

    // ── Helpers HTTP ─────────────────────────────────────────────────

    private String contentUrl() { return "/api/v1/admin/formations/" + bootcamp.getId() + "/content"; }
    private String projectUrl() { return "/api/v1/admin/formations/" + bootcamp.getId() + "/project"; }
    private String bankUrl(String lessonId) { return "/api/v1/admin/lessons/" + lessonId + "/quiz"; }
    private String startUrl() { return "/api/v1/learner/quizzes/" + quizId + "/attempts"; }
    private String overviewUrl() { return "/api/v1/learner/formations/" + bootcamp.getId() + "/evaluations"; }

    private ResultActions complete(String lessonId) throws Exception {
        return mockMvc.perform(put("/api/v1/learner/lessons/" + lessonId + "/progress").with(awa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true,\"positionSeconds\":0}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor staff(String role) {
        return user("staff@test.local", role);
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
