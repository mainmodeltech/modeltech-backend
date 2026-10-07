package com.modeltech.datamasteryhub.modules.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.notification.service.PaymentNotice;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Candidature → acceptation → lien de paiement → déclaration → confirmation → compte et accès apprenant. */
class PaymentFlowIT extends AbstractIntegrationTest {

    private static final String ADMIN_REGISTRATIONS = "/api/v1/admin/registrations";
    private static final String ADMIN_PAYMENTS = "/api/v1/admin/payments";
    private static final String PUBLIC_PAYMENTS = "/api/v1/payments";

    @MockBean private StorageService storageService;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private PaymentService paymentService;

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

    // ── Acceptation : montant et échéances ───────────────────────────

    @Test
    void accept_usesTheBasePrice_andMovesToPaymentPending_andSendsTheLink() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);

        accept(reg, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(150_000))
                .andExpect(jsonPath("$.data.payerType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.data.paymentSummary.installmentCount").value(1))
                .andExpect(jsonPath("$.data.paymentSummary.confirmedCount").value(0));

        PaymentNotice notice = captureLinkNotice();
        assertThat(notice.to()).isEqualTo("awa@example.com");
        assertThat(notice.amount()).isEqualTo(150_000L);
        assertThat(notice.link()).contains("/paiement/");
        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getAcceptedBy()).isEqualTo("staff@test.local");
    }

    @Test
    void accept_appliesEarlyBirdThenPromoPercent() throws Exception {
        session.setEarlyBirdAmount(120_000L);
        session.setEarlyBirdDeadline(LocalDate.now().plusDays(5));
        sessionRepository.save(session);
        Registration reg = newRegistration("awa@example.com", 10);   // 120 000 - 10 % = 108 000

        accept(reg, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(108_000));
    }

    @Test
    void accept_ignoresAnExpiredEarlyBird() throws Exception {
        session.setEarlyBirdAmount(120_000L);
        session.setEarlyBirdDeadline(LocalDate.now().minusDays(1));
        sessionRepository.save(session);
        Registration reg = newRegistration("awa@example.com", 10);   // 150 000 - 10 % = 135 000

        accept(reg, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(135_000));
    }

    @Test
    void accept_withoutNumericPriceNeedsAnExplicitTotal() throws Exception {
        bootcamp.setPriceAmount(null);
        bootcampRepository.save(bootcamp);
        Registration reg = newRegistration("awa@example.com", null);

        accept(reg, "{}").andExpect(status().isBadRequest());
        accept(reg, "{\"totalAmount\":90000,\"payerType\":\"COMPANY\",\"purchaseOrderRef\":\"BC-42\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(90_000))
                .andExpect(jsonPath("$.data.payerType").value("COMPANY"));
        assertThat(paymentRepository.findAll()).extracting(Payment::getPurchaseOrderRef).containsExactly("BC-42");
    }

    @Test
    void accept_splitsInstallmentsEvenly_remainderOnTheFirst() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        bootcamp.setPriceAmount(100_000L);
        bootcampRepository.save(bootcamp);

        accept(reg, "{\"installments\":[{\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"},{\"dueDate\":\"2030-03-10\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentSummary.installmentCount").value(3))
                .andExpect(jsonPath("$.data.paymentSummary.nextDueDate").value("2030-01-10"));

        List<Payment> payments = paymentRepository
                .findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId());
        assertThat(payments).extracting(Payment::getAmount).containsExactly(33_334L, 33_333L, 33_333L);
        assertThat(payments).extracting(Payment::getInstallmentCount).containsOnly(3);
        assertThat(payments).extracting(Payment::getPublicToken).doesNotHaveDuplicates();
        // Seul le lien de la première échéance part à l'acceptation
        verify(notificationService, times(1)).sendPaymentLinkEmail(any(PaymentNotice.class), anyBoolean());
    }

    @Test
    void accept_validatesTheInstallments() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);

        // somme ≠ total
        accept(reg, "{\"installments\":[{\"amount\":100000,\"dueDate\":\"2030-01-10\"},{\"amount\":10000,\"dueDate\":\"2030-02-10\"}]}")
                .andExpect(status().isBadRequest());
        // montants partiellement renseignés
        accept(reg, "{\"installments\":[{\"amount\":150000,\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"}]}")
                .andExpect(status().isBadRequest());
        // dates non croissantes
        accept(reg, "{\"installments\":[{\"dueDate\":\"2030-02-10\"},{\"dueDate\":\"2030-01-10\"}]}")
                .andExpect(status().isBadRequest());
        // date manquante
        accept(reg, "{\"installments\":[{\"amount\":150000}]}").andExpect(status().isBadRequest());

        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.PENDING);
    }

    @Test
    void accept_onlyFromPending_andNotForABackOfficeEmail() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        accept(reg, "{}").andExpect(status().isConflict());

        Registration staff = newRegistration("admin@model-technologie.com", null);
        accept(staff, "{}").andExpect(status().isConflict());
    }

    @Test
    void rejectRegistration_needsAReason_andOnlyFromPending() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);

        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Profil hors cible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectedReason").value("Profil hors cible"));
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Encore\"}"))
                .andExpect(status().isConflict());
    }

    // ── Lien public ──────────────────────────────────────────────────

    @Test
    void publicLink_showsThePaymentWithoutPersonalDataOrOtherTokens() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{\"installments\":[{\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"}]}").andExpect(status().isOk());
        String token = firstToken(reg);

        String body = mockMvc.perform(get(PUBLIC_PAYMENTS + "/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Awa"))
                .andExpect(jsonPath("$.data.bootcampTitle").value("Power BI"))
                .andExpect(jsonPath("$.data.amount").value(75_000))
                .andExpect(jsonPath("$.data.installmentNumber").value(1))
                .andExpect(jsonPath("$.data.installmentCount").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(150_000))
                .andExpect(jsonPath("$.data.schedule.length()").value(2))
                .andExpect(jsonPath("$.data.payTo.methods.length()").value(3))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("awa@example.com").doesNotContain("771234567");
        paymentRepository.findAll().forEach(p -> assertThat(body)
                .as("jeton de l'échéance " + p.getInstallmentNumber()).doesNotContain(p.getPublicToken()));
    }

    @Test
    void publicLink_unknownIs404_expiredIs410() throws Exception {
        mockMvc.perform(get(PUBLIC_PAYMENTS + "/inconnu")).andExpect(status().isNotFound());

        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        Payment p = paymentRepository.findAll().get(0);
        p.setTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(p);

        mockMvc.perform(get(PUBLIC_PAYMENTS + "/" + p.getPublicToken())).andExpect(status().isGone());
        mockMvc.perform(post(PUBLIC_PAYMENTS + "/" + p.getPublicToken() + "/declaration")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"WAVE\",\"reference\":\"T1\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void declare_movesToPaymentToConfirm_andAlertsTheTeam_once() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        String token = firstToken(reg);

        // moyens réservés à l'admin, corps invalide
        declare(token, "{\"method\":\"ENTREPRISE\",\"reference\":\"X\"}").andExpect(status().isBadRequest());
        declare(token, "{\"method\":\"WAVE\"}").andExpect(status().isBadRequest());

        declare(token, "{\"method\":\"WAVE\",\"reference\":\"  TXN-123  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLARED"))
                .andExpect(jsonPath("$.data.reference").value("TXN-123"));

        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.PAYMENT_TO_CONFIRM);
        ArgumentCaptor<PaymentNotice> notice = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(notificationService).notifyPaymentDeclared(notice.capture());
        assertThat(notice.getValue().reference()).isEqualTo("TXN-123");

        declare(token, "{\"method\":\"WAVE\",\"reference\":\"AUTRE\"}").andExpect(status().isConflict());
    }

    @Test
    void uploadProof_isStoredAndReplacesThePreviousOne() throws Exception {
        when(storageService.upload(any(), eq("payment-proofs")))
                .thenReturn(new StorageService.UploadResult("payment-proofs/a.png", "http://minio/a.png"))
                .thenReturn(new StorageService.UploadResult("payment-proofs/b.png", "http://minio/b.png"));
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        String token = firstToken(reg);
        MockMultipartFile file = new MockMultipartFile("file", "capture.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart(PUBLIC_PAYMENTS + "/" + token + "/proof").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasProof").value(true));
        mockMvc.perform(multipart(PUBLIC_PAYMENTS + "/" + token + "/proof").file(file)).andExpect(status().isOk());

        verify(storageService).delete("payment-proofs/a.png");
        assertThat(paymentRepository.findAll().get(0).getProofObjectKey()).isEqualTo("payment-proofs/b.png");
    }

    // ── Confirmation : compte et accès ───────────────────────────────

    @Test
    void confirm_confirmsTheRegistration_reservesASeat_createsTheLearnerAndTheEnrollment() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        declare(firstToken(reg), "{\"method\":\"ORANGE_MONEY\",\"reference\":\"OM-9\"}").andExpect(status().isOk());
        Payment payment = paymentRepository.findAll().get(0);

        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payment.getId() + "/confirm").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedBy").value("staff@test.local"));

        Registration saved = registrationRepository.findById(reg.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(saved.getLearner()).isNotNull();
        assertThat(saved.getLearner().getEmail()).isEqualTo("awa@example.com");
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);

        var enrollment = enrollmentRepository.findByRegistrationIdAndIsDeletedFalse(reg.getId()).orElseThrow();
        assertThat(enrollment.getLearner().getId()).isEqualTo(saved.getLearner().getId());
        assertThat(enrollment.getAccessStartsAt()).isEqualTo(session.getStartDate());
        assertThat(enrollment.getAccessEndsAt()).isEqualTo(session.getEndDate());

        verify(notificationService).sendRegistrationConfirmedEmail(any(Registration.class));
        verify(notificationService).sendAccountInvitationEmail(
                eq("awa@example.com"), eq("Awa"), anyString(), anyInt(), eq(true));

        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payment.getId() + "/confirm").with(admin()))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/admin/enrollments").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].learnerEmail").value("awa@example.com"))
                .andExpect(jsonPath("$.data[0].bootcampTitle").value("Power BI"));
    }

    @Test
    void confirm_firstInstallmentOpensAccess_nextOnesDoNot() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{\"installments\":[{\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"}]}").andExpect(status().isOk());
        List<Payment> payments = paymentRepository
                .findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId());

        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payments.get(0).getId() + "/confirm").with(admin()))
                .andExpect(status().isOk());
        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);

        // la 2e échéance se déclare et se confirme alors que l'inscription est déjà CONFIRMED
        declare(payments.get(1).getPublicToken(), "{\"method\":\"WAVE\",\"reference\":\"W2\"}").andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payments.get(1).getId() + "/confirm").with(admin()))
                .andExpect(status().isOk());

        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);
        assertThat(enrollmentRepository.findAll()).hasSize(1);
        assertThat(learnerRepository.findAll().stream().filter(l -> l.getEmail().equals("awa@example.com"))).hasSize(1);
        verify(notificationService, times(1)).sendRegistrationConfirmedEmail(any(Registration.class));

        mockMvc.perform(get(ADMIN_REGISTRATIONS + "/" + reg.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentSummary.confirmedCount").value(2))
                .andExpect(jsonPath("$.paymentSummary.paidAmount").value(150_000));
    }

    @Test
    void confirm_reusesTheLearnerAccountOfARepeatCustomer() throws Exception {
        Registration first = newRegistration("awa@example.com", null);
        Registration second = newRegistration("AWA@example.com", null);
        for (Registration reg : List.of(first, second)) {
            accept(reg, "{}").andExpect(status().isOk());
        }
        for (Payment p : paymentRepository.findAll()) {
            mockMvc.perform(post(ADMIN_PAYMENTS + "/" + p.getId() + "/confirm").with(admin())).andExpect(status().isOk());
        }
        assertThat(learnerRepository.findAll().stream().filter(l -> l.getEmail().equalsIgnoreCase("awa@example.com")))
                .hasSize(1);
        assertThat(enrollmentRepository.findAll()).hasSize(2);
    }

    // ── Refus, saisie manuelle, relances ─────────────────────────────

    @Test
    void rejectPayment_returnsToPaymentPending_andTellsTheCandidate() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        declare(firstToken(reg), "{\"method\":\"WAVE\",\"reference\":\"FAUX\"}").andExpect(status().isOk());
        Payment payment = paymentRepository.findAll().get(0);

        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payment.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payment.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Référence introuvable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Référence introuvable"));

        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.PAYMENT_PENDING);
        ArgumentCaptor<PaymentNotice> notice = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(notificationService).sendPaymentRejectedEmail(notice.capture());
        assertThat(notice.getValue().reason()).isEqualTo("Référence introuvable");
        assertThat(notice.getValue().reference()).isEqualTo("FAUX");

        // il peut déclarer à nouveau
        declare(firstToken(reg), "{\"method\":\"WAVE\",\"reference\":\"VRAI\"}").andExpect(status().isOk());
        // un paiement qui n'est pas déclaré ne se refuse pas
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + UUID.randomUUID() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualPayment_marksTheInstallmentDeclared_forTheTeamToConfirm() throws Exception {
        Registration reg = newRegistration("entreprise@example.com", null);
        accept(reg, "{\"payerType\":\"COMPANY\"}").andExpect(status().isOk());

        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/payments").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"ENTREPRISE\",\"invoiceRef\":\"FAC-2026-7\",\"purchaseOrderRef\":\"BC-9\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DECLARED"))
                .andExpect(jsonPath("$.data.method").value("ENTREPRISE"))
                .andExpect(jsonPath("$.data.invoiceRef").value("FAC-2026-7"));

        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.PAYMENT_TO_CONFIRM);
        mockMvc.perform(get(ADMIN_PAYMENTS).with(admin()).param("status", "DECLARED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].paymentLink").value(org.hamcrest.Matchers.containsString("/paiement/")));

        // plus d'échéance en attente
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/payments").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"ESPECES\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void remind_resendsTheLink_onlyForPendingPayments() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        Payment payment = paymentRepository.findAll().get(0);

        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payment.getId() + "/remind").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminderCount").value(1));
        verify(notificationService).sendPaymentLinkEmail(any(PaymentNotice.class), eq(true));

        declare(payment.getPublicToken(), "{\"method\":\"WAVE\",\"reference\":\"R\"}").andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payment.getId() + "/remind").with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void scheduler_remindsAfterTwoDays_thenEveryTwoDays_upToThreeTimes() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        accept(reg, "{}").andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now();

        assertThat(paymentService.sendDueReminders(now.plusDays(1))).isZero();
        assertThat(paymentService.sendDueReminders(now.plusDays(2).plusMinutes(1))).isEqualTo(1);
        assertThat(paymentService.sendDueReminders(now.plusDays(2).plusHours(2))).isZero();   // déjà relancé
        assertThat(paymentService.sendDueReminders(now.plusDays(4).plusMinutes(2))).isEqualTo(1);
        assertThat(paymentService.sendDueReminders(now.plusDays(6).plusMinutes(3))).isEqualTo(1);
        assertThat(paymentService.sendDueReminders(now.plusDays(8).plusMinutes(4))).isZero(); // plafond atteint

        verify(notificationService, times(3)).sendPaymentLinkEmail(any(PaymentNotice.class), eq(true));
        assertThat(paymentRepository.findAll().get(0).getReminderCount()).isEqualTo(3);
    }

    @Test
    void scheduler_neverRemindsPaidOrCancelledRegistrations() throws Exception {
        Registration paid = newRegistration("paid@example.com", null);
        Registration cancelled = newRegistration("cancelled@example.com", null);
        accept(paid, "{}").andExpect(status().isOk());
        accept(cancelled, "{}").andExpect(status().isOk());
        Payment paidPayment = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(paid.getId()).get(0);
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + paidPayment.getId() + "/confirm").with(admin())).andExpect(status().isOk());

        mockMvc.perform(patch(ADMIN_REGISTRATIONS + "/" + cancelled.getId() + "/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        assertThat(paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(cancelled.getId()))
                .extracting(Payment::getStatus).containsOnly(PaymentStatus.CANCELLED);

        assertThat(paymentService.sendDueReminders(LocalDateTime.now().plusDays(10))).isZero();
    }

    // ── Compatibilité et sécurité ────────────────────────────────────

    @Test
    void legacyManualConfirmation_stillWorks_withoutCreatingAnAccount() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);

        mockMvc.perform(patch(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);
        assertThat(learnerRepository.findAll().stream().filter(l -> l.getEmail().equals("awa@example.com"))).isEmpty();
    }

    @Test
    void adminEndpointsRequireAnAdminRole() throws Exception {
        Registration reg = newRegistration("awa@example.com", null);
        String[] urls = {ADMIN_PAYMENTS, "/api/v1/admin/enrollments"};

        for (String url : urls) {
            mockMvc.perform(get(url)).andExpect(status().isForbidden());
            mockMvc.perform(get(url).with(user("editeur@test.local", "EDITOR"))).andExpect(status().isForbidden());
            mockMvc.perform(get(url).with(user("apprenant@test.local", "LEARNER"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/accept")
                        .with(user("editeur@test.local", "EDITOR")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(notificationService, never()).sendPaymentLinkEmail(any(PaymentNotice.class), anyBoolean());
    }

    @Test
    void registrationList_exposesThePaymentSummaryForTheKanban() throws Exception {
        Registration pending = newRegistration("nouveau@example.com", null);
        Registration accepted = newRegistration("accepte@example.com", null);
        accept(accepted, "{}").andExpect(status().isOk());

        String body = mockMvc.perform(get(ADMIN_REGISTRATIONS).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode rows = objectMapper.readTree(body).get("content");
        for (JsonNode row : rows) {
            boolean isAccepted = row.get("id").asText().equals(accepted.getId().toString());
            assertThat(row.path("paymentSummary").isNull()).as(row.get("email").asText()).isEqualTo(!isAccepted);
        }
        assertThat(pending.getId()).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Registration newRegistration(String email, Integer discountPercent) {
        return registrationRepository.save(TestData.registration(email, bootcamp, session, discountPercent));
    }

    private org.springframework.test.web.servlet.ResultActions accept(Registration reg, String body) throws Exception {
        return mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/accept").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions declare(String token, String body) throws Exception {
        return mockMvc.perform(post(PUBLIC_PAYMENTS + "/" + token + "/declaration")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String firstToken(Registration reg) {
        return paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId())
                .get(0).getPublicToken();
    }

    private PaymentNotice captureLinkNotice() {
        ArgumentCaptor<PaymentNotice> notice = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(notificationService).sendPaymentLinkEmail(notice.capture(), eq(false));
        return notice.getValue();
    }

    private static RequestPostProcessor admin() {
        return user("staff@test.local", "ADMIN");
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
