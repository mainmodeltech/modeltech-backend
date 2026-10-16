package com.modeltech.datamasteryhub.modules.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.audit.entity.AuditEntry;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Journal d'audit (3b) et séparation des tâches (3c) : remboursements et paiements saisis hors site. */
class AuditAndFourEyesIT extends AbstractIntegrationTest {

    private static final String REGISTRATIONS = "/api/v1/admin/registrations";
    private static final String PAYMENTS = "/api/v1/admin/payments";
    private static final String AUDIT = "/api/v1/admin/audit";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AuditRepository auditRepository;
    @Autowired private JdbcTemplate jdbc;

    private Bootcamp bootcamp;
    private BootcampSession session;

    @BeforeEach
    void setUp() {
        bootcamp = TestData.bootcamp("power-bi", "Power BI", null);
        bootcamp.setPriceAmount(150_000L);
        bootcamp = bootcampRepository.save(bootcamp);
        session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().plusDays(30), SessionStatus.OPEN);
        session.setEndDate(LocalDate.now().plusDays(60));
        session = sessionRepository.save(session);
    }

    // ── Remboursement : demandé par une personne, validé par une autre ───────────

    @Test
    void refund_isRequested_thenApprovedByAnotherPerson() throws Exception {
        colleague("fin2@test.local", "ROLE_FINANCE");
        Payment paid = confirmedPayment("awa@example.com");

        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Doublon de paiement\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.refundRequestedBy").value("fin1@test.local"));

        // le demandeur ne valide pas sa propre demande tant qu'une autre personne habilitée existe
        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund/approve").with(finance1()))
                .andExpect(status().isForbidden());
        // une deuxième demande sur la même échéance est refusée
        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"encore\"}")).andExpect(status().isConflict());

        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund/approve").with(finance2()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundReason").value("Doublon de paiement"));

        assertThat(actions("fin1@test.local")).contains("REFUND_REQUESTED");
        AuditEntry approved = lastEntry("REFUND_APPROVED");
        assertThat(approved.getActorEmail()).isEqualTo("fin2@test.local");
        assertThat(approved.getFlag()).isNull();
        assertThat(approved.getActorProfiles()).contains("FINANCE");
    }

    @Test
    void refund_executesImmediately_andIsFlagged_whenTheRequesterIsTheOnlyPersonWhoCanApprove() throws Exception {
        Payment paid = confirmedPayment("awa@example.com");

        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Annulation client\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundUnchecked").value(true));

        assertThat(lastEntry("REFUND_APPROVED").getFlag()).isEqualTo("NO_SECOND_CHECK");

        // l'audit retrouve les actions sans second contrôle
        JsonNode flagged = json(mockMvc.perform(get(AUDIT).param("flag", "ANY").with(TestAuth.as("audit@test.local", "AUDITOR")))
                .andExpect(status().isOk()));
        assertThat(flagged.at("/data").size()).isGreaterThanOrEqualTo(1);
        assertThat(flagged.at("/data/0/flagLabel").asText()).isEqualTo("Sans second contrôle");
    }

    @Test
    void refundRequest_canBeDeclined() throws Exception {
        colleague("fin2@test.local", "ROLE_FINANCE");
        Payment paid = confirmedPayment("awa@example.com");
        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Erreur\"}")).andExpect(status().isOk());

        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund/decline").with(finance2()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Le client a bien payé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.refundRequestedBy").doesNotExist());

        assertThat(lastEntry("REFUND_DECLINED").getSummary()).contains("Le client a bien payé");
        // une nouvelle demande est possible ensuite
        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Après vérification\"}")).andExpect(status().isOk());
    }

    @Test
    void refundApproval_needsItsOwnPermission() throws Exception {
        colleague("fin2@test.local", "ROLE_FINANCE");
        Payment paid = confirmedPayment("awa@example.com");
        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Erreur\"}")).andExpect(status().isOk());

        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund/approve").with(TestAuth.as("adm@test.local", "ADMISSIONS")))
                .andExpect(status().isForbidden());
    }

    // ── Paiement saisi hors site : confirmé par une autre personne ───────────────

    @Test
    void offsitePayment_cannotBeConfirmedByItsRecorder_whenAnotherConfirmerExists() throws Exception {
        colleague("fin2@test.local", "ROLE_FINANCE");
        Registration reg = acceptedRegistration("awa@example.com");

        // Admissions saisit le virement mais ne peut pas le confirmer
        mockMvc.perform(post(REGISTRATIONS + "/" + reg.getId() + "/payments").with(TestAuth.as("adm@test.local", "ADMISSIONS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"VIREMENT\",\"reference\":\"VIR-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recordedBy").value("adm@test.local"));
        Payment declared = firstPayment(reg);
        assertThat(declared.getStatus()).isEqualTo(PaymentStatus.DECLARED);
        mockMvc.perform(post(PAYMENTS + "/" + declared.getId() + "/confirm").with(TestAuth.as("adm@test.local", "ADMISSIONS")))
                .andExpect(status().isForbidden());

        // la Finance confirme
        mockMvc.perform(post(PAYMENTS + "/" + declared.getId() + "/confirm").with(finance1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertThat(lastEntry("PAYMENT_RECORDED").getActorEmail()).isEqualTo("adm@test.local");
        AuditEntry confirmed = lastEntry("PAYMENT_CONFIRMED");
        assertThat(confirmed.getActorEmail()).isEqualTo("fin1@test.local");
        assertThat(confirmed.getFlag()).isNull();
    }

    @Test
    void offsitePayment_recordedThenConfirmedByTheSamePerson_isRefused_whenAnotherConfirmerExists() throws Exception {
        colleague("fin2@test.local", "ROLE_FINANCE");
        Registration reg = acceptedRegistration("awa@example.com");
        mockMvc.perform(post(REGISTRATIONS + "/" + reg.getId() + "/payments").with(finance1())
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"ESPECES\"}")).andExpect(status().isCreated());
        Payment declared = firstPayment(reg);

        mockMvc.perform(post(PAYMENTS + "/" + declared.getId() + "/confirm").with(finance1())).andExpect(status().isForbidden());
        mockMvc.perform(post(PAYMENTS + "/" + declared.getId() + "/confirm").with(finance2())).andExpect(status().isOk());
    }

    @Test
    void offsitePayment_recordedAndConfirmedByTheSoleApprover_isAllowedButFlagged() throws Exception {
        Registration reg = acceptedRegistration("awa@example.com");
        mockMvc.perform(post(REGISTRATIONS + "/" + reg.getId() + "/payments").with(finance1())
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"VIREMENT\",\"reference\":\"VIR-2\"}")).andExpect(status().isCreated());
        Payment declared = firstPayment(reg);

        mockMvc.perform(post(PAYMENTS + "/" + declared.getId() + "/confirm").with(finance1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedUnchecked").value(true));
        assertThat(lastEntry("PAYMENT_CONFIRMED").getFlag()).isEqualTo("NO_SECOND_CHECK");
    }

    @Test
    void paymentDeclaredByTheLearner_isConfirmedByASinglePerson_withoutFlag() throws Exception {
        colleague("fin2@test.local", "ROLE_FINANCE");
        Registration reg = acceptedRegistration("awa@example.com");
        Payment first = firstPayment(reg);
        mockMvc.perform(post("/api/v1/payments/" + first.getPublicToken() + "/declaration")
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"WAVE\",\"reference\":\"W-1\"}")).andExpect(status().isOk());

        mockMvc.perform(post(PAYMENTS + "/" + first.getId() + "/confirm").with(finance1())).andExpect(status().isOk());
        assertThat(lastEntry("PAYMENT_CONFIRMED").getFlag()).isNull();
    }

    @Test
    void anInactiveOrForgottenAccount_doesNotBlockASmallTeam() throws Exception {
        AdminUser forgotten = colleague("old@test.local", "ROLE_FINANCE");
        forgotten.setLastLoginAt(LocalDateTime.now().minusDays(400));
        adminUserRepository.save(forgotten);
        colleague("off@test.local", "ROLE_FINANCE").setActive(false);

        Payment paid = confirmedPayment("awa@example.com");
        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Annulation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    // ── Journal d'audit ──────────────────────────────────────────────────────────

    @Test
    void acceptingAtADifferentPrice_isMarkedAsAnOverride() throws Exception {
        Registration reg = registrationRepository.save(TestData.registration("awa@example.com", bootcamp, session, null));
        mockMvc.perform(post(REGISTRATIONS + "/" + reg.getId() + "/accept").with(TestAuth.as("staff@test.local", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"totalAmount\":100000}")).andExpect(status().isOk());

        AuditEntry entry = lastEntry("REGISTRATION_ACCEPTED");
        assertThat(entry.getFlag()).isEqualTo("OVERRIDE");
        assertThat(((Number) entry.getDetails().get("tarifCatalogue")).longValue()).isEqualTo(150000L);
    }

    @Test
    void auditLog_isRestrictedToAuditViewers_andCannotBeRewritten() throws Exception {
        Payment paid = confirmedPayment("awa@example.com");
        mockMvc.perform(post(PAYMENTS + "/" + paid.getId() + "/refund").with(finance1()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isOk());

        mockMvc.perform(get(AUDIT).with(TestAuth.as("adm@test.local", "ADMISSIONS"))).andExpect(status().isForbidden());
        mockMvc.perform(get(AUDIT).with(finance1())).andExpect(status().isForbidden());
        JsonNode page = json(mockMvc.perform(get(AUDIT).param("action", "REFUND_APPROVED").with(TestAuth.as("audit@test.local", "AUDITOR")))
                .andExpect(status().isOk()));
        assertThat(page.at("/data/0/actorEmail").asText()).isEqualTo("fin1@test.local");
        assertThat(page.at("/data/0/actionLabel").asText()).isEqualTo("Remboursement effectué");

        mockMvc.perform(get(AUDIT + "/catalogue").with(TestAuth.as("audit@test.local", "AUDITOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.actions").isArray());

        // la base elle-même refuse la modification et la suppression
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET summary = 'réécrit'")).hasMessageContaining("ajout seul");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Un autre compte du back-office, actif et connecté récemment, portant ce profil. */
    private AdminUser colleague(String email, String role) {
        AdminUser u = new AdminUser();
        u.setEmail(email);
        u.setFullName(email);
        u.setPasswordHash("x");
        u.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByNameAndIsDeletedFalse(role).orElseThrow())));
        u.setLastLoginAt(LocalDateTime.now().minusDays(1));
        return adminUserRepository.save(u);
    }

    private Registration acceptedRegistration(String email) throws Exception {
        Registration reg = registrationRepository.save(TestData.registration(email, bootcamp, session, null));
        mockMvc.perform(post(REGISTRATIONS + "/" + reg.getId() + "/accept").with(TestAuth.as("staff@test.local", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"installments\":[{\"dueDate\":\"2030-01-10\"}]}"))
                .andExpect(status().isOk());
        return reg;
    }

    private Payment firstPayment(Registration reg) {
        return paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId()).get(0);
    }

    /** Paiement confirmé (déclaré par l'apprenant lui-même, donc sans contrainte de second contrôle). */
    private Payment confirmedPayment(String email) throws Exception {
        Registration reg = acceptedRegistration(email);
        Payment first = firstPayment(reg);
        mockMvc.perform(post("/api/v1/payments/" + first.getPublicToken() + "/declaration")
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"WAVE\",\"reference\":\"T-1\"}")).andExpect(status().isOk());
        mockMvc.perform(post(PAYMENTS + "/" + first.getId() + "/confirm").with(TestAuth.as("staff@test.local", "ADMIN")))
                .andExpect(status().isOk());
        return paymentRepository.findById(first.getId()).orElseThrow();
    }

    private AuditEntry lastEntry(String action) {
        return auditRepository.findAll().stream().filter(e -> e.getAction().equals(action))
                .max(java.util.Comparator.comparing(AuditEntry::getOccurredAt)).orElseThrow();
    }

    private List<String> actions(String actor) {
        return auditRepository.findAll().stream().filter(e -> e.getActorEmail().equals(actor)).map(AuditEntry::getAction).toList();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor finance1() {
        return TestAuth.as("fin1@test.local", "FINANCE");
    }

    private static RequestPostProcessor finance2() {
        return TestAuth.as("fin2@test.local", "FINANCE");
    }
}
