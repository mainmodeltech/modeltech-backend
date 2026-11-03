package com.modeltech.datamasteryhub.modules.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.payments.GatewayHealth;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutRequest;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutSession;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayException;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayPayment;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.PaymentState;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentCheckoutRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paiement en ligne d'une inscription : une échéance ou tout le solde, confirmation automatique après relecture chez le prestataire,
 * secours manuel, entreprises toujours en manuel, et refus de confirmer quoi que ce soit d'incohérent.
 */
class RegistrationOnlinePaymentIT extends AbstractIntegrationTest {

    private static final String PAYMENTS = "/api/v1/payments";
    private static final String ADMIN_REGISTRATIONS = "/api/v1/admin/registrations";

    @MockBean private StorageService storageService;
    @MockBean private PaymentGateway gateway;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentCheckoutRepository checkoutRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AuditRepository audit;
    @Autowired private GatewayHealth health;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Bootcamp bootcamp;
    private BootcampSession session;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicReference<CheckoutRequest> lastRequest = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        health.reset();
        bootcamp = TestData.bootcamp("power-bi", "Power BI", null);
        bootcamp.setPriceAmount(90_000L);
        bootcamp = bootcampRepository.save(bootcamp);
        session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().plusDays(30), SessionStatus.OPEN);
        session.setEndDate(LocalDate.now().plusDays(60));
        session = sessionRepository.save(session);

        when(gateway.configured()).thenReturn(true);
        when(gateway.provider()).thenReturn("PAYDUNYA");
        when(gateway.mode()).thenReturn("sandbox");
        when(gateway.isGenuineNotificationHash("good")).thenReturn(true);
        when(gateway.createCheckout(any())).thenAnswer(inv -> {
            CheckoutRequest r = inv.getArgument(0);
            lastRequest.set(r);
            int n = counter.incrementAndGet();
            return new CheckoutSession("test_tok" + n, "https://pay.test/" + n);
        });
        mode("ONLINE_AND_MANUAL");
    }

    private void mode(String mode) {
        em.flush();
        jdbc.update("UPDATE payment_service_setting SET mode = ? WHERE service = 'REGISTRATION'", mode);
        em.clear();
    }

    /** Candidature acceptée en 3 échéances de 30 000 FCFA (90 000 au total). */
    private Registration accepted(String email, String extraJson) throws Exception {
        Registration reg = registrationRepository.save(TestData.registration(email, bootcamp, session, null));
        mockMvc.perform(post(ADMIN_REGISTRATIONS + "/" + reg.getId() + "/accept").with(TestAuth.as("staff@test.local", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + extraJson + "\"installments\":[{\"dueDate\":\"2030-01-10\"},{\"dueDate\":\"2030-02-10\"},{\"dueDate\":\"2030-03-10\"}]}"))
                .andExpect(status().isOk());
        return reg;
    }

    private List<Payment> installments(Registration reg) {
        return paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId());
    }

    private String token(Registration reg, int index) {
        return installments(reg).get(index).getPublicToken();
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions startOnline(String token, String scope) throws Exception {
        return mockMvc.perform(post(PAYMENTS + "/" + token + "/online").contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"" + scope + "\"}"));
    }

    private void ipn(String providerToken) throws Exception {
        mockMvc.perform(post(PAYMENTS + "/paydunya/ipn").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data[invoice][token]", providerToken).param("data[hash]", "good")).andExpect(status().isOk());
        em.flush();
        em.clear();
    }

    private void gatewayConfirms(long amount) {
        CheckoutRequest r = lastRequest.get();
        when(gateway.fetchStatus(anyString())).thenReturn(new GatewayPayment(PaymentState.COMPLETED, amount, r.orderRef(), "test", "https://receipt.test/r", null));
    }

    // ── Moyens proposés ──────────────────────────────────────────────

    @Test
    void thePaymentPageShowsWhichMeansAreOffered_andWhatEachOneCosts() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        JsonNode o = json(mockMvc.perform(get(PAYMENTS + "/" + token(reg, 0))).andExpect(status().isOk())).at("/data/online");
        assertThat(o.at("/online").asBoolean()).isTrue();
        assertThat(o.at("/manual").asBoolean()).isTrue();
        assertThat(o.at("/installment/total").asLong()).isEqualTo(30_000);
        assertThat(o.at("/balance/count").asInt()).isEqualTo(3);
        assertThat(o.at("/balance/total").asLong()).isEqualTo(90_000);

        mode("MANUAL_ONLY");        // réglage par défaut : rien de changé pour les inscriptions
        JsonNode m = json(mockMvc.perform(get(PAYMENTS + "/" + token(reg, 0)))).at("/data/online");
        assertThat(m.at("/online").asBoolean()).isFalse();
        assertThat(m.at("/manual").asBoolean()).isTrue();
        assertThat(m.at("/fallback").asBoolean()).isFalse();
    }

    @Test
    void manualOnlyAndCompanyPayers_cannotStartAnOnlinePayment() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        mode("MANUAL_ONLY");
        startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isConflict());

        mode("ONLINE_AND_MANUAL");
        Registration company = accepted("rh@entreprise.com", "\"payerType\":\"COMPANY\",\"purchaseOrderRef\":\"BC-7\",");
        startOnline(token(company, 0), "INSTALLMENT").andExpect(status().isConflict());   // sur facture : toujours en manuel
        assertThat(json(mockMvc.perform(get(PAYMENTS + "/" + token(company, 0)))).at("/data/online/online").asBoolean()).isFalse();
    }

    @Test
    void whenTheServiceIsOnlineOnly_theManualDeclarationIsRefused_untilTheGatewayFails() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        mode("ONLINE_ONLY");
        mockMvc.perform(post(PAYMENTS + "/" + token(reg, 0) + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-1\"}")).andExpect(status().isConflict());

        health.recordFailure();
        health.recordFailure();
        health.recordFailure();        // passerelle en panne : le manuel prend le relais
        assertThat(json(mockMvc.perform(get(PAYMENTS + "/" + token(reg, 0)))).at("/data/online/fallback").asBoolean()).isTrue();
        mockMvc.perform(post(PAYMENTS + "/" + token(reg, 0) + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-1\"}")).andExpect(status().isOk());
    }

    // ── Une échéance ─────────────────────────────────────────────────

    @Test
    void payingOneInstallmentOnline_confirmsItAutomatically_andOpensTheEnrollment() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        JsonNode started = json(startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isOk())).at("/data");
        assertThat(started.at("/checkoutUrl").asText()).startsWith("https://pay.test/");
        assertThat(lastRequest.get().amountXof()).isEqualTo(30_000);
        assertThat(lastRequest.get().returnUrl()).contains("/paiement/" + token(reg, 0)).contains("checkout=" + started.at("/checkoutRef").asText());

        // avant le paiement : rien n'est confirmé
        assertThat(installments(reg)).extracting(Payment::getStatus).containsOnly(PaymentStatus.PENDING);

        gatewayConfirms(30_000);
        ipn("test_tok1");
        List<Payment> after = installments(reg);
        assertThat(after.get(0).getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(after.get(0).getMethod()).isEqualTo(PaymentMethod.PAYDUNYA);
        assertThat(after.get(0).getConfirmedBy()).isEqualTo("paiement-en-ligne");
        assertThat(after.get(1).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(enrollmentRepository.findByRegistrationIdAndIsDeletedFalse(reg.getId())).isPresent();
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("PAYMENT_CONFIRMED");

        // une notification rejouée ne confirme rien de plus
        ipn("test_tok1");
        assertThat(audit.findAll().stream().filter(a -> a.getAction().equals("PAYMENT_CONFIRMED"))).hasSize(1);
        JsonNode view = json(mockMvc.perform(get(PAYMENTS + "/online-checkouts/" + started.at("/checkoutRef").asText())).andExpect(status().isOk())).at("/data");
        assertThat(view.at("/status").asText()).isEqualTo("PAID");
        assertThat(view.at("/receiptUrl").asText()).isEqualTo("https://receipt.test/r");
        assertThat(view.toString().toLowerCase()).doesNotContain("token");
    }

    @Test
    void theSameRequestWithinHalfAnHourReusesTheSameLink() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        String first = json(startOnline(token(reg, 0), "INSTALLMENT")).at("/data/checkoutRef").asText();
        String second = json(startOnline(token(reg, 0), "INSTALLMENT")).at("/data/checkoutRef").asText();
        assertThat(second).isEqualTo(first);
        verify(gateway, times(1)).createCheckout(any());
        startOnline(token(reg, 0), "BALANCE").andExpect(status().isOk());      // autre portée : nouvelle tentative
        verify(gateway, times(2)).createCheckout(any());
    }

    // ── Tout le solde ────────────────────────────────────────────────

    @Test
    void payingTheWholeBalance_confirmsEveryRemainingInstallmentInOneGo() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        startOnline(token(reg, 1), "BALANCE").andExpect(status().isOk());
        assertThat(lastRequest.get().amountXof()).isEqualTo(90_000);

        gatewayConfirms(90_000);
        ipn("test_tok1");
        assertThat(installments(reg)).extracting(Payment::getStatus).containsOnly(PaymentStatus.CONFIRMED);
        assertThat(installments(reg)).extracting(Payment::getMethod).containsOnly(PaymentMethod.PAYDUNYA);
        assertThat(registrationRepository.findById(reg.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(enrollmentRepository.findByRegistrationIdAndIsDeletedFalse(reg.getId())).isPresent();
        assertThat(checkoutRepository.findAll()).extracting(c -> c.getStatus()).containsExactly(CheckoutStatus.PAID);
    }

    @Test
    void theBalanceOnlyCoversWhatIsStillToPay() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isOk());
        gatewayConfirms(30_000);
        ipn("test_tok1");

        JsonNode o = json(mockMvc.perform(get(PAYMENTS + "/" + token(reg, 1)))).at("/data/online");
        assertThat(o.at("/balance/count").asInt()).isEqualTo(2);
        assertThat(o.at("/balance/total").asLong()).isEqualTo(60_000);
        startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isConflict());      // déjà réglée
    }

    // ── Rien d'incohérent n'est confirmé ─────────────────────────────

    @Test
    void aPaymentForAnotherAmountConfirmsNothing_andRaisesAnAlert() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isOk());
        gatewayConfirms(10_000);                  // le prestataire a encaissé 10 000 au lieu de 30 000
        ipn("test_tok1");

        assertThat(installments(reg)).extracting(Payment::getStatus).containsOnly(PaymentStatus.PENDING);
        assertThat(enrollmentRepository.findByRegistrationIdAndIsDeletedFalse(reg.getId())).isEmpty();
        assertThat(checkoutRepository.findAll()).extracting(c -> c.getStatus()).containsExactly(CheckoutStatus.FAILED);
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("PAYMENT_ONLINE_MISMATCH");
    }

    @Test
    void anUnknownTokenIsIgnored() throws Exception {
        ipn("test_inconnu");
        assertThat(checkoutRepository.findAll()).isEmpty();
    }

    @Test
    void ifTheInstallmentWasSettledMeanwhile_theOnlineMoneyIsFlaggedNotCountedTwice() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isOk());

        // l'équipe a entre-temps encaissé la même échéance à la main
        Payment p = installments(reg).get(0);
        p.setStatus(PaymentStatus.CONFIRMED);
        p.setMethod(PaymentMethod.WAVE);
        paymentRepository.save(p);
        em.flush();
        em.clear();

        gatewayConfirms(30_000);
        ipn("test_tok1");
        assertThat(installments(reg).get(0).getMethod()).isEqualTo(PaymentMethod.WAVE);        // inchangée
        assertThat(checkoutRepository.findAll()).extracting(c -> c.getStatus()).containsExactly(CheckoutStatus.FAILED);
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("PAYMENT_ONLINE_MISMATCH");
    }

    // ── Panne du prestataire ─────────────────────────────────────────

    @Test
    void whenTheGatewayIsDown_theBuyerIsToldToPayManually_andAfterThreeFailuresTheBreakerOpens() throws Exception {
        Registration reg = accepted("awa@example.com", "");
        doThrow(new GatewayException("injoignable")).when(gateway).createCheckout(any());
        for (int i = 0; i < 3; i++) {
            startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isBadGateway());
        }
        JsonNode o = json(mockMvc.perform(get(PAYMENTS + "/" + token(reg, 0)))).at("/data/online");
        assertThat(o.at("/online").asBoolean()).isFalse();
        assertThat(o.at("/manual").asBoolean()).isTrue();
        assertThat(o.at("/fallback").asBoolean()).isTrue();
        startOnline(token(reg, 0), "INSTALLMENT").andExpect(status().isConflict());     // plus d'appel au prestataire tant qu'il est en panne
    }
}
