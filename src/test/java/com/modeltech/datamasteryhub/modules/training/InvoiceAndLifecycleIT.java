package com.modeltech.datamasteryhub.modules.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.config.InvoiceProperties;
import com.modeltech.datamasteryhub.modules.notification.service.InvoiceNotice;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.EnrollmentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inscription manuelle, annulation, remboursement et factures PDF des entreprises. */
class InvoiceAndLifecycleIT extends AbstractIntegrationTest {

    private static final String ADMIN_REGISTRATIONS = "/api/v1/admin/registrations";
    private static final String ADMIN_PAYMENTS = "/api/v1/admin/payments";
    private static final String YEAR = String.valueOf(LocalDate.now().getYear());

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private InvoiceProperties invoiceProperties;

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
        invoiceProperties.setVatPercent(BigDecimal.ZERO);
        invoiceProperties.getSeller().setName("Model Technologie");
        invoiceProperties.getSeller().setAddress("Dakar, Sénégal");
        invoiceProperties.getSeller().setTaxId("");
    }

    // ── Inscription manuelle ─────────────────────────────────────────

    @Test
    void manualRegistration_isRecordedAsPending_withoutAnyAutomaticEmail_thenFollowsTheNormalFlow() throws Exception {
        String body = """
                {"sessionId":"%s","firstName":"Awa","lastName":"Diop","email":"awa@example.com","country":"Sénégal",
                 "profile":"PROFESSIONAL","company":"ACME SA","position":"DAF"}""".formatted(session.getId());

        JsonNode created = json(mockMvc.perform(post(ADMIN_REGISTRATIONS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.source").value("ADMIN"))
                .andExpect(jsonPath("$.data.bootcampTitle").value("Power BI")));
        verify(notificationService, never()).sendRegistrationPendingEmail(any());
        verify(notificationService, never()).notifyNewRegistration(any());

        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + created.at("/data/id").asText() + "/accept").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"));

        mockMvc.perform(post(ADMIN_REGISTRATIONS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN_REGISTRATIONS).with(user("e@test.local", "EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // un candidat « professionnel » sans entreprise est refusé comme sur le site
        mockMvc.perform(post(ADMIN_REGISTRATIONS).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@b.co\",\"country\":\"SN\",\"profile\":\"PROFESSIONAL\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Annulation et remboursement ──────────────────────────────────

    @Test
    void cancelling_aConfirmedRegistration_freesTheSeat_closesTheAccess_andCancelsOpenInstallments() throws Exception {
        Registration reg = confirmedRegistration("awa@example.com",
                "{\"installments\":[{\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"}]}");
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);

        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/cancel").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/cancel").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Désistement\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledReason").value("Désistement"));

        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getCurrentParticipants()).isZero();
        assertThat(enrollmentRepository.findAll().get(0).getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        List<Payment> payments = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId());
        assertThat(payments).extracting(Payment::getStatus).containsExactly(PaymentStatus.CONFIRMED, PaymentStatus.CANCELLED);

        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/cancel").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Encore\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void refund_isRecorded_andCancelsTheRegistrationOnlyWhenNothingRemainsPaid() throws Exception {
        Registration reg = confirmedRegistration("awa@example.com",
                "{\"installments\":[{\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"}]}");
        List<Payment> payments = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId());
        Payment second = payments.get(1);
        declareAndConfirm(second);

        // remboursement de la 2e échéance : l'inscription reste confirmée (la 1re est toujours payée)
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + second.getId() + "/refund").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Erreur de montant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundReason").value("Erreur de montant"));
        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);

        // remboursement de la dernière échéance payée : inscription annulée, place libérée
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payments.get(0).getId() + "/refund").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Annulation par le client\"}"))
                .andExpect(status().isOk());
        Registration after = registrationRepository.findById(reg.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(after.getCancelledReason()).isEqualTo("Remboursement : Annulation par le client");
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getCurrentParticipants()).isZero();

        // on ne rembourse ni deux fois ni ce qui n'a pas été payé
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + second.getId() + "/refund").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isConflict());
    }

    // ── Factures ─────────────────────────────────────────────────────

    @Test
    void invoice_isNumberedContinuously_figedAtIssue_andRenderedAsAPdf() throws Exception {
        Registration reg = acceptedRegistration("daf@acme.test", "ACME SA",
                "{\"payerType\":\"COMPANY\",\"purchaseOrderRef\":\"BC-2026-77\","
                        + "\"installments\":[{\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"}]}");

        JsonNode first = json(mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/invoice").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"buyerAddress\":\"Plateau, Dakar\",\"notes\":\"Merci de régler par virement.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.number").value("FAC-" + YEAR + "-00001"))
                .andExpect(jsonPath("$.data.buyerName").value("ACME SA"))
                .andExpect(jsonPath("$.data.purchaseOrderRef").value("BC-2026-77"))
                .andExpect(jsonPath("$.data.total").value(150_000))
                .andExpect(jsonPath("$.data.vatAmount").value(0))
                .andExpect(jsonPath("$.data.dueDate").value("2030-01-10")));
        String number = first.at("/data/number").asText();

        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/invoice").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());

        byte[] pdf = mockMvc.perform(get("/api/v1/admin/invoices/" + number + "/pdf").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument preview = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {   // aperçu pour contrôle visuel
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(preview).renderImageWithDPI(0, 80), "png",
                    java.nio.file.Path.of("target", "invoice-preview.png").toFile());
        }
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("FACTURE", number, "ACME SA", "Plateau, Dakar", "BC-2026-77", "Power BI", "Cohorte 1",
                "150 000 FCFA", "75 000 FCFA", "TOTAL", "Model Technologie", "Dakar, Sénégal");

        // un changement ultérieur des coordonnées du vendeur ne modifie pas la facture émise
        invoiceProperties.getSeller().setName("Autre nom");
        assertThat(new PdfTextExtractor(new PdfReader(
                mockMvc.perform(get("/api/v1/admin/invoices/" + number + "/pdf").with(admin())).andReturn().getResponse().getContentAsByteArray()))
                .getTextFromPage(1)).contains("Model Technologie").doesNotContain("Autre nom");

        // annulation puis réémission : numéro suivant, pas de trou ni de réutilisation
        mockMvc.perform(post("/api/v1/admin/invoices/" + number + "/cancel").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/invoices/" + number + "/cancel").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Mauvaise raison sociale\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(new PdfTextExtractor(new PdfReader(
                mockMvc.perform(get("/api/v1/admin/invoices/" + number + "/pdf").with(admin())).andReturn().getResponse().getContentAsByteArray()))
                .getTextFromPage(1)).contains("ANNUL");
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/invoice").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"buyerName\":\"ACME Sénégal SARL\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.number").value("FAC-" + YEAR + "-00002"));
        mockMvc.perform(get(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/invoices").with(admin()))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void invoice_showsTheEarlyBirdPriceAndThePromoDiscount_andTheVatIncludedInTheTotal() throws Exception {
        session.setEarlyBirdAmount(120_000L);
        session.setEarlyBirdDeadline(LocalDate.now().plusDays(5));
        sessionRepository.save(session);
        invoiceProperties.setVatPercent(new BigDecimal("18"));
        Registration reg = registrationRepository.save(TestData.registration("daf@acme.test", bootcamp, session, 10));
        reg.setCompany("ACME SA");
        reg.setPromoCodeUsed("PARTENAIRE10");
        registrationRepository.save(reg);
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/accept").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        // 120 000 (early-bird) − 10 % = 108 000 TTC dont 18 % de TVA
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/invoice").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitAmount").value(120_000))
                .andExpect(jsonPath("$.data.discountAmount").value(12_000))
                .andExpect(jsonPath("$.data.total").value(108_000))
                .andExpect(jsonPath("$.data.totalExclVat").value(91_525))
                .andExpect(jsonPath("$.data.vatAmount").value(16_475))
                .andExpect(jsonPath("$.data.description").value(org.hamcrest.Matchers.containsString("early-bird")));
        String text = new PdfTextExtractor(new PdfReader(mockMvc.perform(get("/api/v1/admin/invoices/FAC-" + YEAR + "-00001/pdf").with(admin()))
                .andReturn().getResponse().getContentAsByteArray())).getTextFromPage(1);
        assertThat(text).contains("PARTENAIRE10", "10 %", "TVA 18 %", "108 000 FCFA", "TOTAL TTC");
    }

    @Test
    void invoice_requiresAnAcceptedRegistration_andIsRestrictedToTheAdministration() throws Exception {
        Registration pending = registrationRepository.save(TestData.registration("awa@example.com", bootcamp, session, null));

        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + pending.getId() + "/invoice").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/admin/invoices").with(user("e@test.local", "EDITOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/invoices/FAC-1/pdf").with(user("t@test.local", "TRAINER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/invoices/FAC-INCONNU/pdf").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void invoice_canBeEmailed_andDownloadedFromThePaymentLink() throws Exception {
        Registration reg = acceptedRegistration("daf@acme.test", "ACME SA", "{\"payerType\":\"COMPANY\"}");
        String token = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId()).get(0).getPublicToken();

        mockMvc.perform(get("/api/v1/payments/" + token + "/invoice")).andExpect(status().isNotFound());   // pas encore de facture
        mockMvc.perform(get("/api/v1/payments/" + token)).andExpect(jsonPath("$.data.invoiceAvailable").value(false));

        String number = json(mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/invoice").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).at("/data/number").asText();

        mockMvc.perform(get("/api/v1/payments/" + token)).andExpect(jsonPath("$.data.invoiceAvailable").value(true));
        byte[] pdf = mockMvc.perform(get("/api/v1/payments/" + token + "/invoice")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/v1/payments/inconnu/invoice")).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/invoices/" + number + "/send").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"compta@acme.test\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<InvoiceNotice> notice = ArgumentCaptor.forClass(InvoiceNotice.class);
        verify(notificationService).sendInvoiceEmail(notice.capture());
        assertThat(notice.getValue().to()).isEqualTo("compta@acme.test");
        assertThat(notice.getValue().number()).isEqualTo(number);
        assertThat(notice.getValue().total()).isEqualTo(150_000L);
        assertThat(new String(notice.getValue().pdf(), 0, 5)).isEqualTo("%PDF-");

        // une facture annulée n'est plus téléchargeable par le lien ni envoyable
        mockMvc.perform(post("/api/v1/admin/invoices/" + number + "/cancel").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Erreur\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payments/" + token + "/invoice")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/invoices/" + number + "/send").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Registration acceptedRegistration(String email, String company, String acceptBody) throws Exception {
        Registration reg = registrationRepository.save(TestData.registration(email, bootcamp, session, null));
        reg.setCompany(company);
        registrationRepository.save(reg);
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/accept").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(acceptBody)).andExpect(status().isOk());
        return reg;
    }

    /** Candidature acceptée dont la première échéance est confirmée (place prise, compte et accès ouverts). */
    private Registration confirmedRegistration(String email, String acceptBody) throws Exception {
        Registration reg = acceptedRegistration(email, null, acceptBody);
        Payment first = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId()).get(0);
        declareAndConfirm(first);
        return registrationRepository.findById(reg.getId()).orElseThrow();
    }

    private void declareAndConfirm(Payment payment) throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + payment.getPublicToken() + "/declaration")
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"WAVE\",\"reference\":\"T-1\"}")).andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_PAYMENTS + "/" + payment.getId() + "/confirm").with(admin())).andExpect(status().isOk());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor admin() {
        return user("staff@test.local", "ADMIN");
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
