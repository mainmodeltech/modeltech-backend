package com.modeltech.datamasteryhub.modules.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Programme d'une formation (éditeur du back-office) et espace apprenant qui le consomme. */
class CourseContentIT extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private RoleRepository roleRepository;

    private Bootcamp bootcamp;
    private BootcampSession session;

    @BeforeEach
    void setUp() {
        bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().minusDays(7), SessionStatus.IN_PROGRESS);
        session.setEndDate(LocalDate.now().plusDays(60));
        session = sessionRepository.save(session);
    }

    // ── Éditeur (admin) ──────────────────────────────────────────────

    @Test
    void get_withoutProgramme_returnsEmptyModulesAndTheDefaultRules() throws Exception {
        mockMvc.perform(get(url()).with(staff("EDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formationId").value(bootcamp.getId().toString()))
                .andExpect(jsonPath("$.title").value("Power BI"))
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.modules.length()").value(0))
                .andExpect(jsonPath("$.settings.sequentialUnlock").value(true))
                .andExpect(jsonPath("$.settings.accessDuration").value("12_MONTHS"))
                .andExpect(jsonPath("$.certificateRules.lessonsCompletedPercent").value(80));
    }

    @Test
    void put_createsTheTree_withDefinitiveIds_andGetReturnsTheSame() throws Exception {
        JsonNode saved = save(tree("""
                {"settings":{"sequentialUnlock":false,"accessDuration":"LIFETIME"},
                 "certificateRules":{"lessonsCompletedPercent":90,"template":"Co-signé"},
                 "modules":[
                  {"id":"tmp-1","title":"Module 1 — Bases","lessons":[
                    {"id":"tmp-a","title":"1.1 Introduction","type":"VIDEO","status":"PUBLISHED","durationSeconds":600,
                     "videoUrl":"https://video.example.com/a","resources":[
                       {"id":"tmp-r","name":"Support","fileType":"pdf","url":"https://files.example.com/s.pdf"}]},
                    {"title":"1.2 Quiz","type":"QUIZ","status":"DRAFT","quiz":{"questionCount":10,"passThreshold":70,"maxAttempts":3}}]},
                  {"title":"Module 2","lessons":[]}]}"""))
                ;

        assertThat(saved.get("updatedAt").isNull()).isFalse();
        assertThat(saved.at("/settings/sequentialUnlock").asBoolean()).isFalse();
        assertThat(saved.at("/settings/accessDuration").asText()).isEqualTo("LIFETIME");
        assertThat(saved.at("/certificateRules/lessonsCompletedPercent").asInt()).isEqualTo(90);
        assertThat(saved.at("/certificateRules/quizPassPercent").asInt()).isEqualTo(70);   // inchangé : défaut
        assertThat(saved.at("/modules/0/id").asText()).matches("[0-9a-f-]{36}");
        assertThat(saved.at("/modules/0/order").asInt()).isEqualTo(1);
        assertThat(saved.at("/modules/1/order").asInt()).isEqualTo(2);
        assertThat(saved.at("/modules/0/lessons/0/resources/0/fileType").asText()).isEqualTo("PDF");
        assertThat(saved.at("/modules/0/lessons/1/quiz/maxAttempts").asInt()).isEqualTo(3);

        JsonNode reread = json(mockMvc.perform(get(url()).with(staff("ADMIN"))).andExpect(status().isOk()));
        assertThat(reread.at("/modules")).isEqualTo(saved.at("/modules"));
    }

    @Test
    void put_updatesInPlace_reorders_movesLessons_andRemovesWhatIsMissing() throws Exception {
        JsonNode first = save(tree("""
                {"modules":[
                  {"title":"A","lessons":[{"title":"A1","type":"VIDEO","status":"PUBLISHED"},{"title":"A2","type":"VIDEO","status":"PUBLISHED"}]},
                  {"title":"B","lessons":[{"title":"B1","type":"VIDEO","status":"PUBLISHED","resources":[{"name":"R","fileType":"xlsx"}]}]},
                  {"title":"C","lessons":[]}]}"""));
        String modA = first.at("/modules/0/id").asText();
        String modB = first.at("/modules/1/id").asText();
        String a1 = first.at("/modules/0/lessons/0/id").asText();
        String a2 = first.at("/modules/0/lessons/1/id").asText();
        String b1 = first.at("/modules/1/lessons/0/id").asText();

        // B passe devant A, A2 migre dans B, A1 est renommée, le module C et la ressource R disparaissent
        JsonNode second = save(tree("""
                {"modules":[
                  {"id":"%s","title":"B","lessons":[{"id":"%s","title":"B1","type":"VIDEO","status":"PUBLISHED","resources":[]},
                                                    {"id":"%s","title":"A2","type":"VIDEO","status":"PUBLISHED"}]},
                  {"id":"%s","title":"A renommé","lessons":[{"id":"%s","title":"A1 bis","type":"VIDEO","status":"DRAFT"}]}]}"""
                .formatted(modB, b1, a2, modA, a1)));

        assertThat(second.at("/modules/0/id").asText()).isEqualTo(modB);
        assertThat(second.at("/modules/0/lessons/1/id").asText()).isEqualTo(a2);
        assertThat(second.at("/modules/0/lessons/1/title").asText()).isEqualTo("A2");
        assertThat(second.at("/modules/1/id").asText()).isEqualTo(modA);
        assertThat(second.at("/modules/1/lessons/0/title").asText()).isEqualTo("A1 bis");
        assertThat(second.at("/modules/0/lessons/0/resources").size()).isZero();
        assertThat(second.at("/modules").size()).isEqualTo(2);

        // Rien n'est supprimé physiquement (la progression des apprenants référence les leçons)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_modules WHERE is_deleted", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson_resources WHERE is_deleted", Integer.class)).isEqualTo(1);
    }

    @Test
    void put_aTemporaryIdNeverCollidesWithAnotherFormationsElements() throws Exception {
        Bootcamp other = bootcampRepository.save(TestData.bootcamp("excel", "Excel", null));
        JsonNode foreign = json(mockMvc.perform(put("/api/v1/admin/formations/" + other.getId() + "/content")
                        .with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(tree("{\"modules\":[{\"title\":\"Excel M1\",\"lessons\":[]}]}")))
                .andExpect(status().isOk()));
        String foreignModuleId = foreign.at("/modules/0/id").asText();

        JsonNode mine = save(tree("{\"modules\":[{\"id\":\"" + foreignModuleId + "\",\"title\":\"Mon module\",\"lessons\":[]}]}"));

        assertThat(mine.at("/modules/0/id").asText()).isNotEqualTo(foreignModuleId);
        mockMvc.perform(get("/api/v1/admin/formations/" + other.getId() + "/content").with(staff("ADMIN")))
                .andExpect(jsonPath("$.modules[0].title").value("Excel M1"));
    }

    @Test
    void put_validatesTheProgramme_andSavesNothingWhenInvalid() throws Exception {
        String lesson = "{\"title\":\"L\",\"type\":\"VIDEO\",\"status\":\"PUBLISHED\"%s}";
        String[] invalid = {
                "{\"modules\":[{\"title\":\"\",\"lessons\":[]}]}",                                                   // titre vide
                "{\"modules\":[{\"title\":\"M\",\"lessons\":[{\"title\":\"Q\",\"type\":\"QUIZ\",\"status\":\"DRAFT\"}]}]}",   // quiz sans réglages
                "{\"modules\":[{\"title\":\"M\",\"lessons\":[{\"title\":\"Q\",\"type\":\"QUIZ\",\"status\":\"DRAFT\",\"quiz\":{\"questionCount\":5,\"passThreshold\":0}}]}]}",
                "{\"modules\":[{\"title\":\"M\",\"lessons\":[" + lesson.formatted(",\"videoUrl\":\"javascript:alert(1)\"") + "]}]}",
                "{\"modules\":[{\"title\":\"M\",\"lessons\":[{\"title\":\"L\",\"status\":\"PUBLISHED\"}]}]}",          // type manquant
                "{\"modules\":[{\"title\":\"M\",\"lessons\":[" + lesson.formatted(",\"liveAt\":\"demain\"") + "]}]}",
                "{\"settings\":{\"accessDuration\":\"5_YEARS\"},\"modules\":[]}",
                "{\"certificateRules\":{\"quizPassPercent\":150},\"modules\":[]}",
                "{\"modules\":[{\"id\":\"x\",\"title\":\"M1\",\"lessons\":[]},{\"id\":\"x\",\"title\":\"M2\",\"lessons\":[]}]}", // id en double
                "{\"modules\":[{\"title\":\"M\",\"lessons\":[" + lesson.formatted(",\"resources\":[{\"name\":\"R\"}]") + "]}]}",   // type de fichier manquant
        };
        for (String body : invalid) {
            mockMvc.perform(put(url()).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put(url()).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[{\"title\":\"M\",\"lessons\":[{\"title\":\"L\",\"type\":\"INCONNU\",\"status\":\"DRAFT\"}]}]}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_modules", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_configs", Integer.class)).isZero();
    }

    @Test
    void put_liveDates_areStoredInSiteTime_andReturnedAsLocalIso() throws Exception {
        JsonNode saved = save(tree("""
                {"modules":[{"title":"M","lessons":[
                  {"title":"Local","type":"LIVE","status":"SCHEDULED","liveAt":"2026-11-14T18:00","liveUrl":"https://meet.example.com/x"},
                  {"title":"Zulu","type":"LIVE","status":"SCHEDULED","liveAt":"2026-11-14T18:00:00Z"}]}]}"""));

        assertThat(saved.at("/modules/0/lessons/0/liveAt").asText()).isEqualTo("2026-11-14T18:00:00");
        assertThat(saved.at("/modules/0/lessons/1/liveAt").asText()).isEqualTo("2026-11-14T18:00:00");   // Africa/Dakar = UTC
    }

    @Test
    void access_isLimitedToStaffAndTheOwningPartner() throws Exception {
        mockMvc.perform(get(url())).andExpect(status().isForbidden());
        mockMvc.perform(get(url()).with(staff("LEARNER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(url()).with(staff("TRAINER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(url()).with(staff("EDITOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/formations/" + UUID.randomUUID() + "/content").with(staff("ADMIN")))
                .andExpect(status().isNotFound());

        Partner mine = partnerRepository.save(TestData.partner("acme", "Acme"));
        Partner other = partnerRepository.save(TestData.partner("autre", "Autre"));
        bootcamp.setDeliveredBy(DeliveredBy.PARTNER);
        bootcamp.setPartner(mine);
        bootcampRepository.save(bootcamp);
        adminUserRepository.save(partnerAccount("acme@test.local", mine));
        adminUserRepository.save(partnerAccount("autre@test.local", other));

        mockMvc.perform(get(url()).with(user("acme@test.local", "PARTNER"))).andExpect(status().isOk());
        mockMvc.perform(get(url()).with(user("autre@test.local", "PARTNER"))).andExpect(status().isForbidden());
        mockMvc.perform(put(url()).with(user("autre@test.local", "PARTNER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modules\":[]}")).andExpect(status().isForbidden());
    }

    // ── Espace apprenant ─────────────────────────────────────────────

    @Test
    void learnerCourse_hidesDraftsAndWhatIsNotYetOpen() throws Exception {
        enroll("awa@example.com", LocalDate.now().minusDays(1), LocalDate.now().plusDays(60));
        save(sampleProgramme());

        JsonNode course = json(mockMvc.perform(get(learnerCourseUrl()).with(user("awa@example.com", "LEARNER")))
                .andExpect(status().isOk()));

        assertThat(course.get("cohortLabel").asText()).isEqualTo("Cohorte 1");
        JsonNode lessons = course.at("/content/modules/0/lessons");
        assertThat(titles(lessons)).containsExactly("1.1 Intro", "1.2 Bientôt", "1.3 Quiz", "1.4 Live");   // « Brouillon » absent
        assertThat(lessons.get(0).get("videoUrl").asText()).isEqualTo("https://video.example.com/intro");
        assertThat(lessons.get(0).at("/resources/0/url").asText()).isEqualTo("https://files.example.com/open.pdf");
        assertThat(lessons.get(0).at("/resources/1/url").isMissingNode() || lessons.get(0).at("/resources/1/url").isNull())
                .as("ressource verrouillée par quiz").isTrue();
        assertThat(lessons.get(1).get("videoUrl").isNull()).as("leçon programmée : vidéo non livrée").isTrue();
        assertThat(lessons.get(1).get("videoEmbedUrl").isNull()).as("leçon programmée : intégration non livrée").isTrue();
        assertThat(lessons.get(0).get("videoEmbedUrl").isNull()).as("lien direct : pas d'intégration").isTrue();
        assertThat(lessons.get(3).get("liveUrl").asText()).isEqualTo("https://meet.example.com/live");
        assertThat(course.get("progress").size()).isZero();
    }

    @Test
    void learnerCourse_requiresAnOpenEnrollment() throws Exception {
        save(sampleProgramme());
        learnerRepository.save(learner("sans-acces@example.com"));
        mockMvc.perform(get(learnerCourseUrl()).with(user("sans-acces@example.com", "LEARNER")))
                .andExpect(status().isForbidden());

        enroll("futur@example.com", LocalDate.now().plusDays(10), LocalDate.now().plusDays(70));
        mockMvc.perform(get(learnerCourseUrl()).with(user("futur@example.com", "LEARNER")))
                .andExpect(status().isForbidden());

        enroll("ancien@example.com", LocalDate.now().minusDays(500), LocalDate.now().minusDays(400));
        mockMvc.perform(get(learnerCourseUrl()).with(user("ancien@example.com", "LEARNER")))
                .andExpect(status().isForbidden());

        // L'accès « à vie » ne expire pas
        mockMvc.perform(put(url()).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\":{\"accessDuration\":\"LIFETIME\"},\"modules\":[]}")).andExpect(status().isOk());
        mockMvc.perform(get(learnerCourseUrl()).with(user("ancien@example.com", "LEARNER")))
                .andExpect(status().isOk());
    }

    @Test
    void learnerEndpoints_areClosedToStaffAndAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/learner/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/learner/dashboard").with(user("admin@test.local", "ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void progress_isSavedPerLesson_andRulesAreEnforcedByTheServer() throws Exception {
        enroll("awa@example.com", LocalDate.now().minusDays(1), LocalDate.now().plusDays(60));
        JsonNode programme = save(sampleProgramme());
        RequestPostProcessor awa = user("awa@example.com", "LEARNER");
        String intro = programme.at("/modules/0/lessons/0/id").asText();
        String soon = programme.at("/modules/0/lessons/1/id").asText();
        String quiz = programme.at("/modules/0/lessons/2/id").asText();
        String draft = programme.at("/modules/0/lessons/3/id").asText();
        String module2Lesson = programme.at("/modules/1/lessons/0/id").asText();

        // reprise de lecture, puis leçon terminée
        progress(intro, awa, "{\"completed\":false,\"positionSeconds\":125}").andExpect(status().isNoContent());
        progress(intro, awa, "{\"completed\":true,\"positionSeconds\":600}").andExpect(status().isNoContent());
        JsonNode course = json(mockMvc.perform(get(learnerCourseUrl()).with(awa)).andExpect(status().isOk()));
        assertThat(course.at("/progress/0/lessonId").asText()).isEqualTo(intro);
        assertThat(course.at("/progress/0/completed").asBoolean()).isTrue();
        assertThat(course.at("/progress/0/positionSeconds").asInt()).isEqualTo(600);

        progress(soon, awa, "{\"completed\":true,\"positionSeconds\":0}").andExpect(status().isForbidden());   // programmée
        progress(quiz, awa, "{\"completed\":true,\"positionSeconds\":0}").andExpect(status().isForbidden());   // quiz : jamais déclaré
        progress(quiz, awa, "{\"completed\":false,\"positionSeconds\":0}").andExpect(status().isNoContent());
        progress(draft, awa, "{\"completed\":true,\"positionSeconds\":0}").andExpect(status().isNotFound());   // brouillon
        progress(module2Lesson, awa, "{\"completed\":true,\"positionSeconds\":0}").andExpect(status().isForbidden()); // module 1 inachevé
        progress(intro, awa, "{\"positionSeconds\":0}").andExpect(status().isBadRequest());
        progress(intro, awa, "{\"completed\":true,\"positionSeconds\":-1}").andExpect(status().isBadRequest());
        progress(UUID.randomUUID().toString(), awa, "{\"completed\":true,\"positionSeconds\":0}").andExpect(status().isNotFound());

        learnerRepository.save(learner("autre@example.com"));
        progress(intro, user("autre@example.com", "LEARNER"), "{\"completed\":true,\"positionSeconds\":0}")
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_summarisesTheLearnersProgress() throws Exception {
        enroll("awa@example.com", LocalDate.now().minusDays(1), LocalDate.now().plusDays(60));
        JsonNode programme = save(sampleProgramme());
        RequestPostProcessor awa = user("awa@example.com", "LEARNER");
        String intro = programme.at("/modules/0/lessons/0/id").asText();
        progress(intro, awa, "{\"completed\":true,\"positionSeconds\":600}").andExpect(status().isNoContent());

        JsonNode dash = json(mockMvc.perform(get("/api/v1/learner/dashboard").with(awa)).andExpect(status().isOk()));

        assertThat(dash.get("firstName").asText()).isEqualTo("Awa");
        assertThat(dash.at("/stats/lessonsDone").asInt()).isEqualTo(1);
        assertThat(dash.at("/stats/lessonsTotal").asInt()).isEqualTo(5);            // 5 leçons hors brouillon
        assertThat(dash.at("/stats/hoursWatched").asDouble()).isEqualTo(0.2);       // 600 s ≈ 0,2 h
        assertThat(dash.at("/stats/certificates").asInt()).isZero();
        assertThat(dash.get("streakDays").asInt()).isEqualTo(1);
        assertThat(dash.at("/courses/0/title").asText()).isEqualTo("Power BI");
        assertThat(dash.at("/courses/0/progressPercent").asInt()).isEqualTo(20);
        assertThat(dash.at("/courses/0/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(dash.at("/courses/0/providerLabel").asText()).isEqualTo("Model Technologie · Cohorte 1");
        assertThat(dash.at("/resume/lessonTitle").asText()).isEqualTo("1.3 Quiz");     // première leçon publiée restant à faire
        assertThat(dash.at("/resume/lessonLabel").asText()).isEqualTo("1.3");
        assertThat(dash.at("/resume/moduleCount").asInt()).isEqualTo(2);
        assertThat(dash.at("/resume/moduleIndex").asInt()).isEqualTo(1);
        assertThat(dash.at("/lives/0/title").asText()).isEqualTo("1.4 Live");
        assertThat(dash.at("/lives/0/timeLabel").asText()).matches("\\d\\dh\\d\\d");
        assertThat(dash.at("/lives/0/joinUrl").asText()).isEqualTo("https://meet.example.com/live");
        assertThat(dash.at("/todos/0/title").asText()).isEqualTo("1.3 Quiz");   // le quiz à passer est la seule tâche
        assertThat(dash.get("todos").size()).isEqualTo(1);
    }

    @Test
    void dashboard_ofALearnerWithoutEnrollment_isEmptyButValid() throws Exception {
        learnerRepository.save(learner("nouveau@example.com"));
        mockMvc.perform(get("/api/v1/learner/dashboard").with(user("nouveau@example.com", "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(0))
                .andExpect(jsonPath("$.resume").doesNotExist())
                .andExpect(jsonPath("$.stats.lessonsTotal").value(0));
    }

    // ── Données de test ──────────────────────────────────────────────

    /** Module 1 : intro (publiée, 2 ressources dont 1 verrouillée), bientôt (programmée), quiz, brouillon, live. Module 2 : 1 leçon. */
    private String sampleProgramme() {
        String live = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().plusDays(2).withNano(0));
        return tree("""
                {"modules":[
                  {"title":"Module 1","lessons":[
                    {"title":"1.1 Intro","type":"VIDEO","status":"PUBLISHED","durationSeconds":600,"videoUrl":"https://video.example.com/intro",
                     "resources":[{"name":"Support","fileType":"pdf","url":"https://files.example.com/open.pdf"},
                                  {"name":"Corrigé","fileType":"pbix","url":"https://files.example.com/secret.pbix","lockedUntilQuiz":true}]},
                    {"title":"1.2 Bientôt","type":"VIDEO","status":"SCHEDULED","videoUrl":"https://vimeo.com/123456789/abcdef1234"},
                    {"title":"1.3 Quiz","type":"QUIZ","status":"PUBLISHED","quiz":{"questionCount":5,"passThreshold":70,"maxAttempts":2}},
                    {"title":"1.5 Brouillon","type":"VIDEO","status":"DRAFT"},
                    {"title":"1.4 Live","type":"LIVE","status":"PUBLISHED","liveAt":"%s","liveUrl":"https://meet.example.com/live"}]},
                  {"title":"Module 2","lessons":[{"title":"2.1 Suite","type":"VIDEO","status":"PUBLISHED"}]}]}"""
                .formatted(live));
    }

    private String tree(String partial) {
        return partial;
    }

    private void enroll(String email, LocalDate starts, LocalDate ends) {
        Learner learner = learnerRepository.save(learner(email));
        Registration reg = registrationRepository.save(TestData.registration(email, bootcamp, session, null));
        Enrollment e = new Enrollment();
        e.setLearner(learner);
        e.setRegistration(reg);
        e.setSession(session);
        e.setAccessStartsAt(starts);
        e.setAccessEndsAt(ends);
        enrollmentRepository.save(e);
    }

    private Learner learner(String email) {
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName("Awa");
        l.setLastName("Diop");
        return l;
    }

    private AdminUser partnerAccount(String email, Partner partner) {
        AdminUser a = new AdminUser();
        a.setEmail(email);
        a.setFullName("Partenaire");
        a.setPasswordHash("x");
        a.setPartner(partner);
        a.setRoles(new HashSet<>(java.util.List.of(roleRepository.findByName("ROLE_PARTNER").orElseThrow())));
        return a;
    }

    // ── Helpers HTTP ─────────────────────────────────────────────────

    private String url() {
        return "/api/v1/admin/formations/" + bootcamp.getId() + "/content";
    }

    private String learnerCourseUrl() {
        return "/api/v1/learner/formations/" + bootcamp.getId() + "/course";
    }

    private JsonNode save(String body) throws Exception {
        return json(mockMvc.perform(put(url()).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()));
    }

    private ResultActions progress(String lessonId, RequestPostProcessor who, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/learner/lessons/" + lessonId + "/progress").with(who)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private java.util.List<String> titles(JsonNode lessons) {
        java.util.List<String> titles = new java.util.ArrayList<>();
        lessons.forEach(l -> titles.add(l.get("title").asText()));
        return titles;
    }

    private static RequestPostProcessor staff(String role) {
        return user("staff@test.local", role);
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
