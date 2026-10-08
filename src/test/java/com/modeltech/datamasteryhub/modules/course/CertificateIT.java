package com.modeltech.datamasteryhub.modules.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.course.entity.Certificate;
import com.modeltech.datamasteryhub.modules.course.repository.CertificateRepository;
import com.modeltech.datamasteryhub.modules.course.service.CertificateService;
import com.modeltech.datamasteryhub.modules.notification.service.CertificateNotice;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Délivrance automatique et manuelle, vérification publique, PDF, révocation, e-mail. */
class CertificateIT extends AbstractIntegrationTest {

    private static final String ID_FORMAT = "MT-\\d{4}-[A-Z0-9]{2,6}-\\d{5}-[A-HJ-NP-Z2-9]{4}";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private CertificateRepository certificateRepository;
    @Autowired private CertificateService certificateService;

    private Bootcamp bootcamp;
    private BootcampSession session;
    private Learner awa;
    private String lessonId;
    private final RequestPostProcessor awaAuth = user("awa@example.com", "LEARNER");

    @BeforeEach
    void setUp() throws Exception {
        bootcamp = TestData.bootcamp("excel-vba", "Excel VBA — Automatiser ses tâches", null);
        bootcamp.setDuration("4 semaines, 32 h");
        bootcamp.setBenefits(List.of("macros", "procédures VBA", "formulaires"));
        bootcamp = bootcampRepository.save(bootcamp);
        session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().minusDays(7), SessionStatus.IN_PROGRESS);
        session.setEndDate(LocalDate.now().plusDays(60));
        session = sessionRepository.save(session);

        JsonNode programme = json(mockMvc.perform(put("/api/v1/admin/formations/" + bootcamp.getId() + "/content").with(staff("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[{\"title\":\"Module 1\",\"lessons\":[{\"title\":\"1.1 Seule leçon\",\"type\":\"VIDEO\",\"status\":\"PUBLISHED\"}]}]}"))
                .andExpect(status().isOk()));
        lessonId = programme.at("/modules/0/lessons/0/id").asText();
        awa = enroll("awa@example.com");
    }

    // ── Délivrance automatique ───────────────────────────────────────

    @Test
    void completingTheLastCondition_issuesTheCertificate_withAFrozenSnapshot_once() throws Exception {
        assertThat(certificateRepository.findAll()).isEmpty();

        complete().andExpect(status().isNoContent());
        complete().andExpect(status().isNoContent());   // idempotent : jamais deux certificats valides

        List<Certificate> all = certificateRepository.findAll();
        assertThat(all).hasSize(1);
        Certificate c = all.get(0);
        assertThat(c.getPublicId()).matches(ID_FORMAT).contains("-VBA-");
        assertThat(c.getRecipientName()).isEqualTo("Awa Diop");
        assertThat(c.getFormationTitle()).isEqualTo("Excel VBA — Automatiser ses tâches");
        assertThat(c.getDurationLabel()).isEqualTo("4 semaines, 32 h");
        assertThat(c.getSkills()).containsExactly("macros", "procédures VBA", "formulaires");
        assertThat(c.getSignatoryName()).isEqualTo("Patrick Lionnel DOOKO");
        assertThat(c.getSignatoryTitle()).isEqualTo("Gérant, Model Technologie");
        assertThat(c.getIssuedBy()).isEqualTo("système");
        assertThat(c.isForced()).isFalse();
        assertThat(c.isIncludesProject()).isFalse();

        // renommer la formation ne change pas un certificat déjà délivré
        bootcamp.setTitle("Autre titre");
        bootcampRepository.save(bootcamp);
        assertThat(certificateRepository.findAll().get(0).getFormationTitle()).isEqualTo("Excel VBA — Automatiser ses tâches");
    }

    @Test
    void noCertificate_whileAConditionIsMissing_orForAnEmptyProgramme() throws Exception {
        Bootcamp empty = bootcampRepository.save(TestData.bootcamp("vide", "Formation sans programme", null));
        BootcampSession emptySession = TestData.session(empty, "S1", LocalDate.now().minusDays(1), SessionStatus.IN_PROGRESS);
        emptySession = sessionRepository.save(emptySession);
        Learner other = learnerRepository.save(learner("vide@example.com"));
        Registration reg = registrationRepository.save(TestData.registration("vide@example.com", empty, emptySession, null));
        Enrollment e = new Enrollment();
        e.setLearner(other);
        e.setRegistration(reg);
        e.setSession(emptySession);
        e.setAccessStartsAt(emptySession.getStartDate());
        enrollmentRepository.save(e);

        assertThat(certificateService.issueIfEligible(other.getId(), empty.getId())).isEmpty();
        assertThat(certificateService.issueIfEligible(awa.getId(), bootcamp.getId())).isEmpty();   // leçon pas terminée
        assertThat(certificateRepository.findAll()).isEmpty();
    }

    @Test
    void formationCode_comesFromTheFormationOrFromItsTitle() throws Exception {
        assertThat(issueAndGetId()).contains("-VBA-");                       // mot en majuscules du titre

        certificateRepository.findAll().forEach(c -> { c.setStatus(Certificate.REVOKED); certificateRepository.save(c); });
        bootcamp.setCertificateCode("XL");
        bootcampRepository.save(bootcamp);
        mockMvc.perform(post(sessionUrl() + "/learners/" + awa.getId() + "/certificate").with(staff("TRAINER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publicId").value(org.hamcrest.Matchers.containsString("-XL-")));   // code saisi
    }

    // ── Vérification publique et PDF ─────────────────────────────────

    @Test
    void publicVerification_showsOnlyWhatAuthenticatesTheCertificate() throws Exception {
        String publicId = issueAndGetId();

        String body = mockMvc.perform(get("/api/v1/certificates/" + publicId.toLowerCase()))   // casse indifférente
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.recipientName").value("Awa Diop"))
                .andExpect(jsonPath("$.data.formationTitle").value("Excel VBA — Automatiser ses tâches"))
                .andExpect(jsonPath("$.data.signatoryName").value("Patrick Lionnel DOOKO"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("awa@example.com").doesNotContain("771234567");

        mockMvc.perform(get("/api/v1/certificates/MT-2026-XXX-00001-AAAA")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/certificates/MT-2026-VBA-00001")).andExpect(status().isNotFound());   // suffixe manquant
    }

    @Test
    void publicPdf_isARealPdf_untilTheCertificateIsRevoked() throws Exception {
        String publicId = issueAndGetId();

        byte[] pdf = mockMvc.perform(get("/api/v1/certificates/" + publicId + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5_000);

        mockMvc.perform(post("/api/v1/admin/certificates/" + publicId + "/revoke").with(staff("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fraude constatée\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/certificates/" + publicId + "/pdf")).andExpect(status().isGone());
        mockMvc.perform(get("/api/v1/certificates/" + publicId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    // ── Espace apprenant ─────────────────────────────────────────────

    @Test
    void learner_seesTheCertificate_theLinkedInLink_andTheDashboardReflectsIt() throws Exception {
        String publicId = issueAndGetId();

        JsonNode list = json(mockMvc.perform(get("/api/v1/learner/certificates").with(awaAuth)).andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.at("/0/publicId").asText()).isEqualTo(publicId);
        assertThat(list.at("/0/verifyUrl").asText()).endsWith("/certificats/" + publicId);
        assertThat(list.at("/0/pdfPath").asText()).isEqualTo("/api/v1/certificates/" + publicId + "/pdf");
        String linkedIn = list.at("/0/linkedInUrl").asText();
        assertThat(linkedIn).startsWith("https://www.linkedin.com/profile/add?startTask=CERTIFICATION_NAME")
                .contains("organizationId=103600105").contains("certId=" + publicId).contains("issueYear=" + LocalDate.now().getYear());

        JsonNode dash = json(mockMvc.perform(get("/api/v1/learner/dashboard").with(awaAuth)).andExpect(status().isOk()));
        assertThat(dash.at("/stats/certificates").asInt()).isEqualTo(1);
        assertThat(dash.at("/courses/0/status").asText()).isEqualTo("CERTIFIED");
        assertThat(dash.at("/courses/0/statusNote").asText()).contains("certificat disponible");
        assertThat(dash.at("/certificateReady/title").asText()).isEqualTo("Excel VBA — Automatiser ses tâches");

        // les certificats des autres restent privés
        learnerRepository.save(learner("autre@example.com"));
        mockMvc.perform(get("/api/v1/learner/certificates").with(user("autre@example.com", "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/learner/certificates")).andExpect(status().isForbidden());
    }

    // ── Back-office ──────────────────────────────────────────────────

    @Test
    void manualIssue_requiresMetConditions_orAMotivatedAdminOverride() throws Exception {
        String url = sessionUrl() + "/learners/" + awa.getId() + "/certificate";

        mockMvc.perform(post(url).with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Leçons terminées")));
        mockMvc.perform(post(url).with(staff("TRAINER")).contentType(MediaType.APPLICATION_JSON).content("{\"force\":true,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"force\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(sessionUrl() + "/tracking").with(staff("ADMIN")))
                .andExpect(jsonPath("$.learners[0].certificate").value("PENDING"));

        mockMvc.perform(post(url).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":true,\"reason\":\"Absent justifié, rattrapage fait en présentiel\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.forced").value(true))
                .andExpect(jsonPath("$.data.forceReason").value("Absent justifié, rattrapage fait en présentiel"))
                .andExpect(jsonPath("$.data.issuedBy").value("staff@test.local"))
                .andExpect(jsonPath("$.data.status").value("VALID"));

        mockMvc.perform(post(url).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get(sessionUrl() + "/tracking").with(staff("ADMIN")))
                .andExpect(jsonPath("$.learners[0].certificate").value("ISSUED"));
    }

    @Test
    void revocation_needsAReason_isFinal_andNeverRevertedAutomatically() throws Exception {
        String publicId = issueAndGetId();
        String revoke = "/api/v1/admin/certificates/" + publicId + "/revoke";

        mockMvc.perform(post(revoke).with(staff("EDITOR")).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(revoke).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(revoke).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Erreur de saisie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.revokedReason").value("Erreur de saisie"));
        mockMvc.perform(post(revoke).with(staff("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Encore\"}"))
                .andExpect(status().isConflict());

        // un événement ultérieur ne redélivre pas un certificat révoqué
        assertThat(certificateService.issueIfEligible(awa.getId(), bootcamp.getId())).isEmpty();
        complete().andExpect(status().isNoContent());
        assertThat(certificateRepository.findAll()).hasSize(1);

        // la délivrance manuelle reste possible : décision humaine
        mockMvc.perform(post(sessionUrl() + "/learners/" + awa.getId() + "/certificate").with(staff("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/admin/certificates").param("status", "valid").with(staff("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalElements").value(1));
        mockMvc.perform(get("/api/v1/admin/certificates").with(staff("EDITOR"))).andExpect(status().isForbidden());
    }

    @Test
    void notifyIssued_sendsTheCongratulationsWithThePdfAndTheLinks() throws Exception {
        String publicId = issueAndGetId();

        certificateService.notifyIssued(publicId);

        ArgumentCaptor<CertificateNotice> notice = ArgumentCaptor.forClass(CertificateNotice.class);
        verify(notificationService).sendCertificateReadyEmail(notice.capture());
        assertThat(notice.getValue().to()).isEqualTo("awa@example.com");
        assertThat(notice.getValue().firstName()).isEqualTo("Awa");
        assertThat(notice.getValue().publicId()).isEqualTo(publicId);
        assertThat(notice.getValue().verifyUrl()).endsWith("/certificats/" + publicId);
        assertThat(notice.getValue().linkedInUrl()).contains("organizationId=103600105");
        assertThat(new String(notice.getValue().pdf(), 0, 5)).isEqualTo("%PDF-");

        mockMvc.perform(post("/api/v1/admin/certificates/" + publicId + "/resend").with(staff("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/certificates/INCONNU/resend").with(staff("ADMIN"))).andExpect(status().isNotFound());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String issueAndGetId() throws Exception {
        complete().andExpect(status().isNoContent());
        return certificateRepository.findAll().get(0).getPublicId();
    }

    private ResultActions complete() throws Exception {
        return mockMvc.perform(put("/api/v1/learner/lessons/" + lessonId + "/progress").with(awaAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true,\"positionSeconds\":0}"));
    }

    private String sessionUrl() {
        return "/api/v1/admin/sessions/" + session.getId();
    }

    private Learner enroll(String email) {
        Learner learner = learnerRepository.save(learner(email));
        Registration reg = registrationRepository.save(TestData.registration(email, bootcamp, session, null));
        Enrollment e = new Enrollment();
        e.setLearner(learner);
        e.setRegistration(reg);
        e.setSession(session);
        e.setAccessStartsAt(session.getStartDate());
        e.setAccessEndsAt(session.getEndDate());
        enrollmentRepository.save(e);
        return learner;
    }

    private Learner learner(String email) {
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName("Awa");
        l.setLastName("Diop");
        return l;
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
