package com.modeltech.datamasteryhub.modules.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Compléments de l'espace apprenant : profil, paiements et reçus, attestation, calendrier, ressources, « à faire ». */
class LearnerApiIT extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private LearnerRepository learnerRepository;

    private Bootcamp bootcamp;
    private BootcampSession session;
    private Learner awa;
    private Payment paid;
    private Payment due;
    private final RequestPostProcessor asAwa = user("awa@example.com", "LEARNER");
    private final RequestPostProcessor asMoussa = user("moussa@example.com", "LEARNER");

    @BeforeEach
    void setUp() throws Exception {
        bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        bootcamp.setDuration("5 jours");
        bootcamp = bootcampRepository.save(bootcamp);
        session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().minusDays(7), SessionStatus.IN_PROGRESS);
        session.setEndDate(LocalDate.now().plusDays(60));
        session = sessionRepository.save(session);

        awa = learnerRepository.save(learner("awa@example.com", "Awa"));
        learnerRepository.save(learner("moussa@example.com", "Moussa"));

        Registration reg = TestData.registration("awa@example.com", bootcamp, session, null);
        reg.setStatus(RegistrationStatus.CONFIRMED);
        reg.setTotalAmount(150_000L);
        reg.setLearner(awa);
        reg = registrationRepository.save(reg);

        Enrollment enrollment = new Enrollment();
        enrollment.setLearner(awa);
        enrollment.setRegistration(reg);
        enrollment.setSession(session);
        enrollment.setAccessStartsAt(LocalDate.now().minusDays(7));
        enrollment.setAccessEndsAt(LocalDate.now().plusDays(60));
        enrollmentRepository.save(enrollment);

        paid = payment(reg, 1, 100_000L, PaymentStatus.CONFIRMED, LocalDate.now().minusDays(5));
        paid.setMethod(PaymentMethod.WAVE);
        paid.setConfirmedAt(LocalDateTime.now().minusDays(4));
        due = payment(reg, 2, 50_000L, PaymentStatus.PENDING, LocalDate.now().minusDays(1));
        entityManager.flush();

        String live = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().plusDays(3).withNano(0));
        String programme = """
                {"modules":[{"title":"Module 1","lessons":[
                  {"title":"1.1 Intro","type":"VIDEO","status":"PUBLISHED","videoUrl":"https://vimeo.com/123456789/abcdef1234",
                   "resources":[{"name":"Support","fileType":"pdf","url":"https://files.example.com/open.pdf"},
                                {"name":"Corrigé","fileType":"pbix","url":"https://files.example.com/secret.pbix","lockedUntilQuiz":true}]},
                  {"title":"1.2 Quiz","type":"QUIZ","status":"PUBLISHED","quiz":{"questionCount":5,"passThreshold":70,"maxAttempts":2}},
                  {"title":"1.3 Brouillon","type":"VIDEO","status":"DRAFT","resources":[{"name":"Caché","fileType":"pdf","url":"https://files.example.com/draft.pdf"}]},
                  {"title":"1.4 Live","type":"LIVE","status":"PUBLISHED","liveAt":"%s","liveUrl":"https://meet.example.com/live"}]}]}""".formatted(live);
        mockMvc.perform(put("/api/v1/admin/formations/" + bootcamp.getId() + "/content").with(user("staff@test.local", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(programme)).andExpect(status().isOk());
    }

    // ── Profil ───────────────────────────────────────────────────────

    @Test
    void profile_canBeReadAndUpdated_butNotTheEmail() throws Exception {
        mockMvc.perform(get("/api/v1/learner/profile").with(asAwa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("awa@example.com"))
                .andExpect(jsonPath("$.fullName").value("Awa Diop"))
                .andExpect(jsonPath("$.hasPassword").value(false));

        mockMvc.perform(put("/api/v1/learner/profile").with(asAwa).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Awa\",\"lastName\":\"Ndiaye\",\"phone\":\"771112233\",\"country\":\"Sénégal\",\"email\":\"autre@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Ndiaye"))
                .andExpect(jsonPath("$.phone").value("771112233"))
                .andExpect(jsonPath("$.email").value("awa@example.com"));

        mockMvc.perform(put("/api/v1/learner/profile").with(asAwa).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void firstPassword_canBeSetOnce_thenOnlyChanged() throws Exception {
        mockMvc.perform(put("/api/v1/learner/password").with(asAwa).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"court\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/learner/password").with(asAwa).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"MotDePasse#2026\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/learner/profile").with(asAwa)).andExpect(jsonPath("$.hasPassword").value(true));
        mockMvc.perform(put("/api/v1/learner/password").with(asAwa).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"AutreMotDePasse#1\"}"))
                .andExpect(status().isConflict());
    }

    // ── Paiements, reçus, attestation ────────────────────────────────

    @Test
    void payments_listMineWithLinksOnlyWhereTheyApply() throws Exception {
        JsonNode list = json(mockMvc.perform(get("/api/v1/learner/payments").with(asAwa)).andExpect(status().isOk()));
        assertThat(list).hasSize(2);
        JsonNode first = list.get(0);
        assertThat(first.get("installmentNumber").asInt()).isEqualTo(1);
        assertThat(first.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.get("receiptUrl").asText()).isEqualTo("/api/v1/learner/payments/" + paid.getId() + "/receipt");
        assertThat(first.get("payUrl").isNull()).isTrue();
        JsonNode second = list.get(1);
        assertThat(second.get("status").asText()).isEqualTo("PENDING");
        assertThat(second.get("payUrl").asText()).contains("/paiement/" + due.getPublicToken());
        assertThat(second.get("receiptUrl").isNull()).isTrue();
        assertThat(second.get("totalAmount").asLong()).isEqualTo(150_000L);

        mockMvc.perform(get("/api/v1/learner/payments").with(asMoussa)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void receipt_isAPdfForAConfirmedPayment_ofTheOwnerOnly() throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/v1/learner/payments/" + paid.getId() + "/receipt").with(asAwa))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        String text = text(bytes);
        assertThat(text).contains("REÇU DE PAIEMENT").contains("100 000 FCFA").contains("Power BI").contains("Wave")
                .contains("RESTE À RÉGLER").contains("50 000 FCFA").contains("Patrick Lionnel DOOKO").contains("009775076");

        mockMvc.perform(get("/api/v1/learner/payments/" + paid.getId() + "/receipt").with(asMoussa)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learner/payments/" + due.getId() + "/receipt").with(asAwa)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learner/payments/" + paid.getId() + "/receipt")).andExpect(status().isForbidden());
    }

    @Test
    void receipt_isAlsoAvailableFromThePaymentLink() throws Exception {
        mockMvc.perform(get("/api/v1/payments/" + paid.getPublicToken() + "/receipt"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/api/v1/payments/" + due.getPublicToken() + "/receipt")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments/jeton-inconnu/receipt")).andExpect(status().isNotFound());
    }

    @Test
    void enrollments_andAttestation() throws Exception {
        mockMvc.perform(get("/api/v1/learner/enrollments").with(asAwa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Power BI"))
                .andExpect(jsonPath("$[0].sessionName").value("Cohorte 1"))
                .andExpect(jsonPath("$[0].attestationUrl").value("/api/v1/learner/enrollments/" + bootcamp.getId() + "/attestation"));

        byte[] bytes = mockMvc.perform(get("/api/v1/learner/enrollments/" + bootcamp.getId() + "/attestation").with(asAwa))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(text(bytes)).contains("ATTESTATION D'INSCRIPTION").contains("Awa Diop").contains("Power BI").contains("5 jours");

        mockMvc.perform(get("/api/v1/learner/enrollments/" + bootcamp.getId() + "/attestation").with(asMoussa)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learner/enrollments/" + UUID.randomUUID() + "/attestation").with(asAwa)).andExpect(status().isNotFound());
    }

    // ── Calendrier, ressources, tableau de bord ──────────────────────

    @Test
    void calendar_listsLivesOfMyFormations_inTheRequestedWindow() throws Exception {
        mockMvc.perform(get("/api/v1/learner/calendar").with(asAwa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("1.4 Live"))
                .andExpect(jsonPath("$[0].formationTitle").value("Power BI"))
                .andExpect(jsonPath("$[0].joinUrl").value("https://meet.example.com/live"))
                .andExpect(jsonPath("$[0].past").value(false));
        mockMvc.perform(get("/api/v1/learner/calendar").param("from", LocalDate.now().plusDays(10).toString()).with(asAwa))
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/learner/calendar").with(asMoussa)).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/learner/calendar").param("from", "2026-05-10").param("to", "2026-05-01").with(asAwa))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resources_libraryHidesDraftsAndLocksQuizProtectedFiles() throws Exception {
        JsonNode library = json(mockMvc.perform(get("/api/v1/learner/resources").with(asAwa)).andExpect(status().isOk()));
        assertThat(library).hasSize(2);   // le brouillon est absent
        JsonNode open = library.get(0);
        assertThat(open.get("name").asText()).isEqualTo("Support");
        assertThat(open.get("url").asText()).isEqualTo("https://files.example.com/open.pdf");
        assertThat(open.get("locked").asBoolean()).isFalse();
        assertThat(open.get("formationTitle").asText()).isEqualTo("Power BI");
        assertThat(open.get("lessonTitle").asText()).isEqualTo("1.1 Intro");
        JsonNode locked = library.get(1);
        assertThat(locked.get("locked").asBoolean()).isTrue();
        assertThat(locked.get("url").isNull()).as("lien jamais livré tant que verrouillé").isTrue();

        mockMvc.perform(get("/api/v1/learner/resources").with(asMoussa)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void dashboard_todosListThePaymentDueAndTheQuizToTake() throws Exception {
        JsonNode todos = json(mockMvc.perform(get("/api/v1/learner/dashboard").with(asAwa)).andExpect(status().isOk())).get("todos");
        assertThat(todos.size()).isGreaterThanOrEqualTo(2);
        assertThat(todos.get(0).get("title").asText()).isEqualTo("Régler l'échéance 2/2");
        assertThat(todos.get(0).get("badge").asText()).isEqualTo("En retard");
        assertThat(todos.get(0).get("tone").asText()).isEqualTo("warning");
        boolean quiz = false;
        for (JsonNode t : todos) quiz |= "1.2 Quiz".equals(t.get("title").asText());
        assertThat(quiz).isTrue();
    }

    // ── Données ──────────────────────────────────────────────────────

    private Payment payment(Registration reg, int number, long amount, PaymentStatus status, LocalDate dueDate) {
        Payment p = new Payment();
        p.setRegistration(reg);
        p.setInstallmentNumber(number);
        p.setInstallmentCount(2);
        p.setAmount(amount);
        p.setStatus(status);
        p.setDueDate(dueDate);
        p.setPublicToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        p.setTokenExpiresAt(LocalDateTime.now().plusDays(30));
        return paymentRepository.save(p);
    }

    private Learner learner(String email, String firstName) {
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName(firstName);
        l.setLastName("Diop");
        return l;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
