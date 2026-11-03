package com.modeltech.datamasteryhub.modules.coaching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingRequest;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingStatus;
import com.modeltech.datamasteryhub.modules.coaching.repository.CoachingCheckoutRepository;
import com.modeltech.datamasteryhub.modules.coaching.repository.CoachingRequestRepository;
import com.modeltech.datamasteryhub.modules.coaching.service.CoachingService;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.notification.service.TemplatedMailer;
import com.modeltech.datamasteryhub.modules.payments.GatewayHealth;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutRequest;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutSession;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayPayment;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.PaymentState;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Coaching de bout en bout : demande publique → analyse par la pédagogie → validation (formateur confirmé, offre, montant, lien de paiement) →
 * paiement en ligne ou manuel → confirmation. Séparation des tâches : la pédagogie valide, la Finance confirme l'argent.
 */
class CoachingFlowIT extends AbstractIntegrationTest {

    private static final String PUBLIC = "/api/v1/coaching-requests";
    private static final String PAY = "/api/v1/coaching/payments";
    private static final String ADMIN = "/api/v1/admin/coaching-requests";

    @MockBean private TemplatedMailer mailer;
    @MockBean private StorageService storage;
    @MockBean private PaymentGateway gateway;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private CoachingRequestRepository requests;
    @Autowired private CoachingCheckoutRepository checkouts;
    @Autowired private CoachingService coaching;
    @Autowired private AuditRepository audit;
    @Autowired private GatewayHealth health;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicReference<CheckoutRequest> lastRequest = new AtomicReference<>();

    private static RequestPostProcessor pedagogy() {
        return TestAuth.as("pedago@test.local", "PEDAGOGY");
    }

    private static RequestPostProcessor finance() {
        return TestAuth.as("fin@test.local", "FINANCE");
    }

    @BeforeEach
    void setUp() {
        health.reset();
        when(mailer.send(anyString(), anyString(), any(), anyString())).thenReturn(true);
        when(gateway.configured()).thenReturn(true);
        when(gateway.provider()).thenReturn("PAYDUNYA");
        when(gateway.mode()).thenReturn("sandbox");
        when(gateway.isGenuineNotificationHash("good")).thenReturn(true);
        when(gateway.createCheckout(any())).thenAnswer(inv -> {
            lastRequest.set(inv.getArgument(0));
            int n = counter.incrementAndGet();
            return new CheckoutSession("test_ctok" + n, "https://pay.test/c" + n);
        });
        when(storage.uploadPrivateImage(any(), eq("coaching-proofs"))).thenReturn(new StorageService.UploadResult("coaching-proofs/p1.png", null));
        when(storage.presignedGetUrl(eq("coaching-proofs/p1.png"), anyInt())).thenReturn("https://signed.test/p1.png");
    }

    private void mode(String mode) {
        em.flush();
        jdbc.update("UPDATE payment_service_setting SET mode = ? WHERE service = 'COACHING'", mode);
        em.clear();
    }

    private ResultActions submit(String email, boolean terms) throws Exception {
        return mockMvc.perform(post(PUBLIC).contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"" + email + "\",\"phone\":\"771234567\",\"company\":\"Test SA\","
                        + "\"tier\":\"Pro\",\"slot\":\"Mardi matin\",\"message\":\"Je veux progresser sur DAX\",\"acceptTerms\":" + terms + "}"));
    }

    private UUID newRequest(String email) throws Exception {
        submit(email, true).andExpect(status().isCreated());
        em.flush();
        em.clear();
        return requests.findAll().stream().filter(r -> r.getEmail().equals(email)).findFirst().orElseThrow().getId();
    }

    private ResultActions validate(UUID id, String body) throws Exception {
        return mockMvc.perform(post(ADMIN + "/" + id + "/validate").with(pedagogy()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static final String VALID_BODY = "{\"offerLabel\":\"2 séances d'1 h\",\"amountXof\":75000,\"trainerName\":\"Cédric Faye\",\"trainerEmail\":\"cedric@test.local\",\"trainerConfirmed\":true}";

    /** Demande validée, lien de paiement actif ; renvoie le jeton du lien. */
    private String validated(String email) throws Exception {
        UUID id = newRequest(email);
        validate(id, VALID_BODY).andExpect(status().isOk());
        em.flush();
        em.clear();
        return requests.findById(id).orElseThrow().getPayToken();
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions ipn(String providerToken) throws Exception {
        ResultActions r = mockMvc.perform(post("/api/v1/payments/paydunya/ipn").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data[invoice][token]", providerToken).param("data[hash]", "good")).andExpect(status().isOk());
        em.flush();
        em.clear();
        return r;
    }

    private void gatewayConfirms(long amount) {
        when(gateway.fetchStatus(anyString())).thenReturn(new GatewayPayment(PaymentState.COMPLETED, amount, lastRequest.get().orderRef(), "test", "https://receipt.test/c", null));
    }

    private CoachingRequest reload(String token) {
        em.flush();
        em.clear();
        return requests.findByPayToken(token).orElseThrow();
    }

    // ── Demande publique ─────────────────────────────────────────────

    @Test
    void aVisitorSendsARequest_theTeamIsAlerted_andNothingInternalComesBack() throws Exception {
        JsonNode receipt = json(submit("awa@test.local", true).andExpect(status().isCreated())).at("/data");
        assertThat(receipt.at("/message").asText()).contains("Demande envoyée");
        verify(mailer).send(eq("awa@test.local"), contains("bien reçue"), any(), eq("COACHING_RECEIVED"));
        verify(mailer, atLeastOnce()).send(anyString(), contains("Nouvelle demande de coaching"), any(), eq("COACHING_STAFF_NEW"));
        CoachingRequest r = requests.findAll().get(0);
        assertThat(r.getStatus()).isEqualTo(CoachingStatus.NEW);
        assertThat(r.getPayToken()).isNull();                                   // pas de lien tant que l'équipe n'a pas validé
        assertThat(receipt.toString()).doesNotContain("awa@test.local");
    }

    @Test
    void termsMustBeAccepted_andOneAddressCannotFloodTheForm() throws Exception {
        submit("awa@test.local", false).andExpect(status().isBadRequest());
        for (int i = 0; i < 3; i++) submit("flood@test.local", true).andExpect(status().isCreated());
        submit("flood@test.local", true).andExpect(status().isTooManyRequests());
        assertThat(requests.findAll()).filteredOn(r -> r.getEmail().equals("awa@test.local")).isEmpty();
    }

    // ── Traitement par l'équipe : droits ─────────────────────────────

    @Test
    void onlyThePedagogyHandlesRequests_financeOnlyReads_andNobodyAnonymous() throws Exception {
        UUID id = newRequest("awa@test.local");
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN).with(TestAuth.as("t@test.local", "TRAINER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN).with(finance())).andExpect(status().isOk());                       // la Finance voit (paiements à vérifier)
        mockMvc.perform(get(ADMIN + "/" + id).with(pedagogy())).andExpect(status().isOk());
        mockMvc.perform(post(ADMIN + "/" + id + "/take").with(finance())).andExpect(status().isForbidden());
        validate(id, VALID_BODY).andExpect(status().isOk());                                                    // la pédagogie valide
        mockMvc.perform(post(ADMIN + "/" + id + "/payment/confirm").with(pedagogy())).andExpect(status().isForbidden());   // mais ne confirme pas l'argent
    }

    // ── Traitement par l'équipe : parcours ───────────────────────────

    @Test
    void takingARequest_assignsIt_andTheTrainerAndNotesAreKeptInternal() throws Exception {
        UUID id = newRequest("awa@test.local");
        JsonNode taken = json(mockMvc.perform(post(ADMIN + "/" + id + "/take").with(pedagogy())).andExpect(status().isOk())).at("/data");
        assertThat(taken.at("/status").asText()).isEqualTo("IN_REVIEW");
        assertThat(taken.at("/assignedTo").asText()).isEqualTo("pedago@test.local");
        mockMvc.perform(put(ADMIN + "/" + id).with(pedagogy()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trainerName\":\"Cédric Faye\",\"trainerEmail\":\"cedric@test.local\",\"internalNotes\":\"Client sérieux\"}")).andExpect(status().isOk());
        em.flush();
        em.clear();
        CoachingRequest r = requests.findById(id).orElseThrow();
        assertThat(r.getTrainerName()).isEqualTo("Cédric Faye");
        assertThat(r.getInternalNotes()).isEqualTo("Client sérieux");
    }

    @Test
    void validationNeedsTheTrainerConfirmationAndARealAmount_thenSendsThePaymentLink() throws Exception {
        UUID id = newRequest("awa@test.local");
        validate(id, VALID_BODY.replace("\"trainerConfirmed\":true", "\"trainerConfirmed\":false")).andExpect(status().isBadRequest());
        validate(id, VALID_BODY.replace("75000", "50")).andExpect(status().isBadRequest());
        validate(id, "{\"amountXof\":75000,\"trainerConfirmed\":true}").andExpect(status().isBadRequest());

        JsonNode v = json(validate(id, VALID_BODY).andExpect(status().isOk())).at("/data");
        assertThat(v.at("/status").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(v.at("/amountXof").asLong()).isEqualTo(75_000);
        assertThat(v.at("/paymentLink").asText()).contains("/coaching/paiement/");
        verify(mailer).send(eq("awa@test.local"), contains("validé"), any(), eq("COACHING_PAYMENT_LINK"));
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("COACHING_VALIDATED");
        validate(id, VALID_BODY).andExpect(status().isConflict());        // une seule validation
        String token = requests.findById(id).orElseThrow().getPayToken();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);                // 256 bits
    }

    @Test
    void decliningSendsTheReason_andOnlyBeforeValidation() throws Exception {
        UUID id = newRequest("awa@test.local");
        mockMvc.perform(post(ADMIN + "/" + id + "/decline").with(pedagogy()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN + "/" + id + "/decline").with(pedagogy()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Pas de formateur disponible ce trimestre\"}")).andExpect(status().isOk());
        verify(mailer).send(eq("awa@test.local"), anyString(), any(), eq("COACHING_DECLINED"));
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(CoachingStatus.DECLINED);
        validate(id, VALID_BODY).andExpect(status().isConflict());        // refusée : plus rien à valider
    }

    @Test
    void cancellingAValidatedCoaching_killsTheLink() throws Exception {
        String token = validated("awa@test.local");
        UUID id = requests.findByPayToken(token).orElseThrow().getId();
        mockMvc.perform(post(ADMIN + "/" + id + "/cancel").with(pedagogy()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Le client renonce\"}")).andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get(PAY + "/" + token)).andExpect(status().isOk())).at("/data/status").asText()).isEqualTo("CANCELLED");
        mode("ONLINE_AND_MANUAL");
        mockMvc.perform(post(PAY + "/" + token + "/online")).andExpect(status().isConflict());
    }

    // ── Page de paiement ─────────────────────────────────────────────

    @Test
    void thePaymentPageShowsTheOfferWithoutAnyInternalData() throws Exception {
        String token = validated("awa@test.local");
        mockMvc.perform(get(PAY + "/inconnu-token")).andExpect(status().isNotFound());
        String body = mockMvc.perform(get(PAY + "/" + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode v = objectMapper.readTree(body).at("/data");
        assertThat(v.at("/offerLabel").asText()).contains("séances");
        assertThat(v.at("/trainerName").asText()).isEqualTo("Cédric Faye");
        assertThat(v.at("/amountXof").asLong()).isEqualTo(75_000);
        assertThat(body).doesNotContain("awa@test.local").doesNotContain("internalNotes").doesNotContain("771234567").doesNotContain("cedric@test.local");
    }

    @Test
    void anExpiredLinkIsGone_andTheSweepClosesUnpaidOnesButNeverADeclaredOne() throws Exception {
        String t1 = validated("a@test.local");
        String t2 = validated("b@test.local");
        mode("MANUAL_ONLY");
        mockMvc.perform(post(PAY + "/" + t2 + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-1\"}")).andExpect(status().isOk());
        em.flush();
        jdbc.update("UPDATE coaching_request SET token_expires_at = NOW() - INTERVAL '1 day'");
        em.clear();
        mockMvc.perform(get(PAY + "/" + t1)).andExpect(status().isGone());
        coaching.expireOldLinks();
        assertThat(reload(t1).getStatus()).isEqualTo(CoachingStatus.EXPIRED);
        assertThat(reload(t2).getStatus()).isEqualTo(CoachingStatus.PAYMENT_DECLARED);       // un humain doit trancher
    }

    // ── Paiement en ligne ────────────────────────────────────────────

    @Test
    void onlinePayment_confirmsTheCoachingOnlyAfterTheGatewayIsRead() throws Exception {
        String token = validated("awa@test.local");
        mode("MANUAL_ONLY");
        mockMvc.perform(post(PAY + "/" + token + "/online")).andExpect(status().isConflict());        // réglage par défaut : manuel seulement

        mode("ONLINE_AND_MANUAL");
        JsonNode started = json(mockMvc.perform(post(PAY + "/" + token + "/online")).andExpect(status().isOk())).at("/data");
        assertThat(started.at("/checkoutUrl").asText()).startsWith("https://pay.test/");
        assertThat(lastRequest.get().amountXof()).isEqualTo(75_000);
        assertThat(lastRequest.get().returnUrl()).contains("/coaching/paiement/" + token).contains("checkout=" + started.at("/checkoutRef").asText());
        // même demande dans la demi-heure : même lien
        assertThat(json(mockMvc.perform(post(PAY + "/" + token + "/online"))).at("/data/checkoutRef").asText()).isEqualTo(started.at("/checkoutRef").asText());
        assertThat(reload(token).getStatus()).isEqualTo(CoachingStatus.AWAITING_PAYMENT);              // rien n'est payé tant que le prestataire n'a pas confirmé

        gatewayConfirms(75_000);
        ipn("test_ctok1");
        CoachingRequest paid = reload(token);
        assertThat(paid.getStatus()).isEqualTo(CoachingStatus.PAID);
        assertThat(paid.getPaidAmountXof()).isEqualTo(75_000);
        assertThat(paid.getConfirmedBy()).isEqualTo("paiement-en-ligne");
        verify(mailer).send(eq("awa@test.local"), contains("confirmé"), any(), eq("COACHING_PAID"));
        verify(mailer).send(eq("cedric@test.local"), contains("Coaching payé"), any(), eq("COACHING_STAFF_PAID"));   // le formateur est prévenu
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("COACHING_PAID");

        ipn("test_ctok1");        // notification rejouée : rien de plus
        assertThat(audit.findAll().stream().filter(a -> a.getAction().equals("COACHING_PAID"))).hasSize(1);
        JsonNode view = json(mockMvc.perform(get("/api/v1/coaching/checkouts/" + started.at("/checkoutRef").asText())).andExpect(status().isOk())).at("/data");
        assertThat(view.at("/status").asText()).isEqualTo("PAID");
        assertThat(view.toString().toLowerCase()).doesNotContain("token");
    }

    @Test
    void aPaymentForAnotherAmount_confirmsNothingAndRaisesAnAlert() throws Exception {
        String token = validated("awa@test.local");
        mode("ONLINE_AND_MANUAL");
        mockMvc.perform(post(PAY + "/" + token + "/online")).andExpect(status().isOk());
        gatewayConfirms(10_000);
        ipn("test_ctok1");
        assertThat(reload(token).getStatus()).isEqualTo(CoachingStatus.AWAITING_PAYMENT);
        assertThat(checkouts.findAll()).extracting(c -> c.getStatus()).containsExactly(CheckoutStatus.FAILED);
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("PAYMENT_ONLINE_MISMATCH");
    }

    @Test
    void moneyArrivingAfterTheCoachingWasCancelled_isFlaggedNotCounted() throws Exception {
        String token = validated("awa@test.local");
        mode("ONLINE_AND_MANUAL");
        mockMvc.perform(post(PAY + "/" + token + "/online")).andExpect(status().isOk());
        UUID id = reload(token).getId();
        mockMvc.perform(post(ADMIN + "/" + id + "/cancel").with(pedagogy()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Annulé\"}")).andExpect(status().isOk());
        em.flush();
        em.clear();
        gatewayConfirms(75_000);
        ipn("test_ctok1");
        assertThat(reload(token).getStatus()).isEqualTo(CoachingStatus.CANCELLED);
        assertThat(checkouts.findAll()).extracting(c -> c.getStatus()).containsExactly(CheckoutStatus.FAILED);
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("PAYMENT_ONLINE_MISMATCH");
    }

    // ── Paiement manuel ──────────────────────────────────────────────

    @Test
    void manualPayment_isDeclaredByTheClient_thenConfirmedByTheFinance_notByThePedagogy() throws Exception {
        String token = validated("awa@test.local");
        mode("MANUAL_ONLY");
        mockMvc.perform(post(PAY + "/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"WAVE\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(PAY + "/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-777\"}")).andExpect(status().isOk());
        mockMvc.perform(post(PAY + "/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-777\"}")).andExpect(status().isConflict());      // déjà déclaré
        mockMvc.perform(multipart(PAY + "/" + token + "/proof").file(new MockMultipartFile("file", "r.png", "image/png", new byte[]{1}))).andExpect(status().isOk());
        verify(mailer, atLeastOnce()).send(anyString(), contains("à vérifier"), any(), eq("COACHING_STAFF_DECLARED"));
        CoachingRequest declared = reload(token);
        assertThat(declared.getStatus()).isEqualTo(CoachingStatus.PAYMENT_DECLARED);
        UUID id = declared.getId();

        mockMvc.perform(post(ADMIN + "/" + id + "/payment/confirm").with(pedagogy())).andExpect(status().isForbidden());
        assertThat(json(mockMvc.perform(get(ADMIN + "/" + id + "/proof").with(finance())).andExpect(status().isOk())).at("/data/url").asText()).isEqualTo("https://signed.test/p1.png");
        JsonNode done = json(mockMvc.perform(post(ADMIN + "/" + id + "/payment/confirm").with(finance())).andExpect(status().isOk())).at("/data");
        assertThat(done.at("/status").asText()).isEqualTo("PAID");
        assertThat(done.at("/confirmedBy").asText()).isEqualTo("fin@test.local");
        assertThat(done.at("/paidChannel").asText()).isEqualTo("MANUAL");
        mockMvc.perform(post(ADMIN + "/" + id + "/payment/confirm").with(finance())).andExpect(status().isConflict());   // une seule fois
        verify(mailer).send(eq("awa@test.local"), contains("confirmé"), any(), eq("COACHING_PAID"));
    }

    @Test
    void aRejectedManualPayment_goesBackToAwaiting_withTheReasonShownToTheClient() throws Exception {
        String token = validated("awa@test.local");
        mode("MANUAL_ONLY");
        mockMvc.perform(post(PAY + "/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ORANGE_MONEY\",\"reference\":\"OM-1\"}")).andExpect(status().isOk());
        UUID id = reload(token).getId();
        mockMvc.perform(post(ADMIN + "/" + id + "/payment/reject").with(finance()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN + "/" + id + "/payment/reject").with(finance()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Montant reçu inférieur au prix\"}")).andExpect(status().isOk());
        JsonNode v = json(mockMvc.perform(get(PAY + "/" + token)).andExpect(status().isOk())).at("/data");
        assertThat(v.at("/status").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(v.at("/rejectNote").asText()).contains("Montant reçu inférieur");
        mockMvc.perform(post(PAY + "/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-2\"}")).andExpect(status().isOk());      // peut déclarer de nouveau
    }

    @Test
    void whenOnlineOnlyAndTheGatewayAnswers_theManualDeclarationIsRefused() throws Exception {
        String token = validated("awa@test.local");
        mode("ONLINE_ONLY");
        mockMvc.perform(post(PAY + "/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-1\"}")).andExpect(status().isConflict());
        health.recordFailure();
        health.recordFailure();
        health.recordFailure();                                                                             // passerelle en panne : le manuel prend le relais
        mockMvc.perform(post(PAY + "/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-1\"}")).andExpect(status().isOk());
        verify(gateway, never()).createCheckout(any());
    }
}
