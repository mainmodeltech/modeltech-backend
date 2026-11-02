package com.modeltech.datamasteryhub.modules.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.CheckoutRequest;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.CheckoutSession;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.GatewayException;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.GatewayPayment;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.PaymentState;
import com.modeltech.datamasteryhub.modules.simulator.service.OrderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achat en ligne d'un forfait avec une passerelle simulée : prix décidé par le serveur, accès ouvert seulement après vérification
 * auprès du prestataire, un seul accès par commande, incohérences refusées, rapprochement, droits d'administration.
 */
class SimulatorOrdersIT extends AbstractIntegrationTest {

    private static final String API = "/api/v1/simulator";
    private static final String IPN = "/api/v1/payments/paydunya/ipn";
    private static final String ADMIN = "/api/v1/admin/simulator";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private OrderService orderService;
    @Autowired private AuditRepository audit;
    @MockBean private PaymentGateway gateway;

    private final AtomicReference<String> lastRef = new AtomicReference<>();
    private final AtomicReference<GatewayPayment> remote = new AtomicReference<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private CheckoutRequest lastRequest;

    @BeforeEach
    void gateway() {
        when(gateway.configured()).thenReturn(true);
        when(gateway.provider()).thenReturn("PAYDUNYA");
        when(gateway.mode()).thenReturn("sandbox");
        when(gateway.notificationUrl()).thenReturn("https://api.test/api/v1/payments/paydunya/ipn");
        when(gateway.createCheckout(any())).thenAnswer(inv -> {
            lastRequest = inv.getArgument(0);
            lastRef.set(lastRequest.orderRef());
            int n = sequence.incrementAndGet();
            return new CheckoutSession("test_tok" + n, "https://pay.test/checkout/" + n);
        });
        when(gateway.fetchStatus(anyString())).thenAnswer(inv -> remote.get() != null ? remote.get()
                : new GatewayPayment(PaymentState.PENDING, 0, null, null, null, null));
        when(gateway.isGenuineNotificationHash("good-hash")).thenReturn(true);
    }

    private void payRemotely(PaymentState state, long amount, String ref, String mode) {
        remote.set(new GatewayPayment(state, amount, ref, mode, state == PaymentState.COMPLETED ? "https://receipt.test/r.pdf" : null,
                state == PaymentState.FAILED ? "Solde insuffisant" : null));
    }

    private static String body(String plan, String email) {
        return "{\"plan\":\"" + plan + "\",\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"" + email + "\",\"phone\":\"+221770000000\",\"acceptTerms\":true}";
    }

    private JsonNode order(String plan, String email) throws Exception {
        return json(mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON).content(body(plan, email)))
                .andExpect(status().isCreated())).at("/data");
    }

    private ResultActions ipn(String token) throws Exception {
        return mockMvc.perform(post(IPN).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("data[invoice][token]", token)
                .param("data[status]", "completed").param("data[hash]", "good-hash"));
    }

    private void sync() {
        em.flush();
        em.clear();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ── Forfaits publics ─────────────────────────────────────────────

    @Test
    void thePublicPlansShowTheRealPrices_andOnlineSalesAreOffUntilTheGatewayIsConfigured() throws Exception {
        JsonNode plans = json(mockMvc.perform(get(API + "/plans")).andExpect(status().isOk())).at("/data");
        assertThat(plans.at("/paymentsEnabled").asBoolean()).isTrue();
        assertThat(plans.at("/plans")).hasSize(3);
        assertThat(plans.at("/plans/1/code").asText()).isEqualTo("PRO");
        assertThat(plans.at("/plans/1/priceXof").asInt()).isEqualTo(45000);

        when(gateway.configured()).thenReturn(false);
        assertThat(json(mockMvc.perform(get(API + "/plans"))).at("/data/paymentsEnabled").asBoolean()).isFalse();
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON).content(body("PRO", "awa@test.local"))).andExpect(status().isServiceUnavailable());
    }

    // ── Commande ─────────────────────────────────────────────────────

    @Test
    void anOrderIsPricedByTheServer_createsTheAccountWithoutPassword_andOpensNothingYet() throws Exception {
        // le navigateur peut envoyer ce qu'il veut : le prix vient de la base
        JsonNode created = json(mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PRO\",\"priceXof\":1,\"amountXof\":1,\"firstName\":\"Awa\",\"email\":\"awa@test.local\",\"acceptTerms\":true}"))
                .andExpect(status().isCreated())).at("/data");
        assertThat(created.at("/checkoutUrl").asText()).startsWith("https://pay.test/checkout/");
        assertThat(lastRequest.amountXof()).isEqualTo(45000);
        assertThat(lastRequest.returnUrl()).contains("/simulateur-pl-300/paiement?ref=" + created.at("/orderRef").asText());
        assertThat(lastRequest.cancelUrl()).endsWith("&annule=1");

        sync();
        assertThat(count("SELECT count(*) FROM sim_order WHERE status = 'PENDING' AND amount_xof = 45000 AND plan_code = 'PRO' AND provider_token = 'test_tok1'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM learners WHERE email = 'awa@test.local' AND password_hash IS NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM sim_access_grant")).isZero();
        // tant que rien n'est payé, le suivi reste « en attente » et ne révèle qu'une adresse masquée
        JsonNode status = json(mockMvc.perform(get(API + "/orders/" + created.at("/orderRef").asText())).andExpect(status().isOk())).at("/data");
        assertThat(status.at("/status").asText()).isEqualTo("PENDING");
        assertThat(status.at("/emailHint").asText()).isEqualTo("a***@test.local");
        assertThat(status.toString()).doesNotContain("awa@test.local").doesNotContain("test_tok1");
    }

    @Test
    void invalidOrdersAreRefused() throws Exception {
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PRO\",\"firstName\":\"Awa\",\"email\":\"awa@test.local\",\"acceptTerms\":false}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON).content(body("TRIAL", "awa@test.local"))).andExpect(status().isConflict());   // non vendu
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON).content(body("PRO", "x@mailinator.com"))).andExpect(status().isBadRequest());
        String staff = jdbc.queryForObject("SELECT email FROM admin_users WHERE is_deleted = FALSE LIMIT 1", String.class);
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON).content(body("PRO", staff))).andExpect(status().isBadRequest());
        mockMvc.perform(get(API + "/orders/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
        sync();
        assertThat(count("SELECT count(*) FROM sim_order")).isZero();
    }

    @Test
    void ifTheProviderIsDown_theOrderIsClosed_andTheVisitorGetsAClearMessage() throws Exception {
        org.mockito.Mockito.doThrow(new GatewayException("Le prestataire de paiement est momentanément injoignable.")).when(gateway).createCheckout(any());
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON).content(body("PRO", "awa@test.local"))).andExpect(status().isBadGateway());
        sync();
        assertThat(count("SELECT count(*) FROM sim_order WHERE status = 'FAILED'")).isEqualTo(1);
    }

    // ── Paiement confirmé ────────────────────────────────────────────

    @Test
    void aConfirmedPayment_opensExactlyOneAccess_evenIfNotifiedTwice() throws Exception {
        JsonNode created = order("PRO", "awa@test.local");
        payRemotely(PaymentState.COMPLETED, 45000, lastRef.get(), "test");

        ipn("test_tok1").andExpect(status().isOk());
        ipn("test_tok1").andExpect(status().isOk());      // PayDunya peut notifier plusieurs fois
        mockMvc.perform(post(ADMIN + "/orders/" + jdbc.queryForObject("SELECT id FROM sim_order", String.class) + "/verify").with(TestAuth.as("pedago@test.local", "PEDAGOGY")))
                .andExpect(status().isOk());
        sync();

        assertThat(count("SELECT count(*) FROM sim_access_grant")).isEqualTo(1);
        var grant = jdbc.queryForMap("SELECT plan_code, exam_attempts_total, granted_by, source_payment_id::text AS src, (expires_at::date - starts_at::date) AS days FROM sim_access_grant");
        assertThat(grant).containsEntry("plan_code", "PRO").containsEntry("exam_attempts_total", 5).containsEntry("granted_by", "paiement-en-ligne");
        assertThat(((Number) grant.get("days")).intValue()).isEqualTo(90);
        assertThat(jdbc.queryForMap("SELECT status, receipt_url, grant_id::text AS g FROM sim_order")).containsEntry("status", "PAID")
                .containsEntry("receipt_url", "https://receipt.test/r.pdf").containsEntry("g", jdbc.queryForObject("SELECT id::text FROM sim_access_grant", String.class));
        assertThat(grant.get("src")).isEqualTo(jdbc.queryForObject("SELECT id::text FROM sim_order", String.class));
        verify(gateway, times(1)).fetchStatus(anyString());         // une fois confirmée, la commande n'est plus relue
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("SIM_ORDER_PAID");

        JsonNode status = json(mockMvc.perform(get(API + "/orders/" + created.at("/orderRef").asText())).andExpect(status().isOk())).at("/data");
        assertThat(status.at("/status").asText()).isEqualTo("PAID");
        assertThat(status.at("/receiptUrl").asText()).isEqualTo("https://receipt.test/r.pdf");

        // et l'apprenant retrouve son forfait dans son espace
        JsonNode catalog = json(mockMvc.perform(get("/api/v1/learner/simulator/catalog").with(TestAuth.as("awa@test.local", "LEARNER"))).andExpect(status().isOk())).at("/data");
        assertThat(catalog.at("/access/0/planCode").asText()).isEqualTo("PRO");
        assertThat(catalog.at("/access/0/examAttemptsRemaining").asInt()).isEqualTo(5);
    }

    @Test
    void theReturnPageConfirmsThePaymentWithoutWaitingForTheNotification() throws Exception {
        JsonNode created = order("ESSENTIEL", "awa@test.local");
        payRemotely(PaymentState.COMPLETED, 25000, lastRef.get(), "test");
        JsonNode status = json(mockMvc.perform(get(API + "/orders/" + created.at("/orderRef").asText())).andExpect(status().isOk())).at("/data");
        assertThat(status.at("/status").asText()).isEqualTo("PAID");
        sync();
        assertThat(count("SELECT count(*) FROM sim_access_grant WHERE plan_code = 'ESSENTIEL' AND exam_attempts_total = 2")).isEqualTo(1);
    }

    @Test
    void thePendingStatusIsNotPolledMoreThanOnceEveryFewSeconds() throws Exception {
        JsonNode created = order("PRO", "awa@test.local");
        for (int i = 0; i < 4; i++) mockMvc.perform(get(API + "/orders/" + created.at("/orderRef").asText())).andExpect(status().isOk());
        verify(gateway, times(1)).fetchStatus(anyString());
    }

    // ── Rien n'est cru sans vérification ─────────────────────────────

    @Test
    void aPaymentWhoseAmountReferenceOrModeDoesNotMatch_opensNothing() throws Exception {
        for (Object[] c : new Object[][]{{40000L, null, "test"}, {45000L, "autre-reference", "test"}, {45000L, null, "live"}}) {
            jdbc.update("DELETE FROM sim_order");
            sync();
            order("PRO", "awa@test.local");
            payRemotely(PaymentState.COMPLETED, (Long) c[0], c[1] == null ? lastRef.get() : (String) c[1], (String) c[2]);
            ipn("test_tok" + sequence.get()).andExpect(status().isOk());
            sync();
            assertThat(count("SELECT count(*) FROM sim_access_grant")).as(java.util.Arrays.toString(c)).isZero();
            assertThat(jdbc.queryForObject("SELECT status || ' ' || failure_reason FROM sim_order", String.class)).startsWith("FAILED Vérification impossible");
        }
    }

    @Test
    void cancelledAndFailedPaymentsOpenNothing() throws Exception {
        order("PRO", "awa@test.local");
        payRemotely(PaymentState.CANCELLED, 0, lastRef.get(), "test");
        ipn("test_tok1");
        sync();
        assertThat(jdbc.queryForObject("SELECT status FROM sim_order", String.class)).isEqualTo("CANCELLED");

        order("PRO", "awa@test.local");
        payRemotely(PaymentState.FAILED, 0, lastRef.get(), "test");
        ipn("test_tok2");
        sync();
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM sim_order WHERE provider_token = 'test_tok2'", String.class)).isEqualTo("Solde insuffisant");
        assertThat(count("SELECT count(*) FROM sim_access_grant")).isZero();
    }

    @Test
    void aForgedNotificationChangesNothing() throws Exception {
        order("PRO", "awa@test.local");
        payRemotely(PaymentState.COMPLETED, 45000, lastRef.get(), "test");
        // jeton inconnu, jeton absent : réponse neutre, rien ne bouge
        ipn("test_inconnu").andExpect(status().isOk());
        mockMvc.perform(post(IPN).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("data[status]", "completed")).andExpect(status().isOk());
        // vrai jeton mais « hash » qui n'est pas celui du prestataire : ignoré, le prestataire n'est même pas interrogé
        mockMvc.perform(post(IPN).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("data[invoice][token]", "test_tok1").param("data[hash]", "forged"))
                .andExpect(status().isOk());
        verify(gateway, never()).fetchStatus(anyString());
        sync();
        assertThat(count("SELECT count(*) FROM sim_access_grant")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM sim_order", String.class)).isEqualTo("PENDING");
    }

    // ── Rapprochement ────────────────────────────────────────────────

    @Test
    void theReconciliationFindsLostNotifications_andAbandonsStaleOrders() throws Exception {
        order("PRO", "awa@test.local");
        order("PRO", "moussa@test.local");
        sync();
        jdbc.update("UPDATE sim_order SET created_at = NOW() - INTERVAL '10 minutes'");
        remote.set(null);   // le prestataire répond « en attente » pour tous
        payRemotely(PaymentState.COMPLETED, 45000, lastRef.get(), "test");
        // seule la commande de Moussa (dernière référence) correspond : l'autre sera refusée pour incohérence de référence
        sync();
        orderService.reconcile();
        sync();
        assertThat(count("SELECT count(*) FROM sim_order WHERE status = 'PAID'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM sim_order WHERE status = 'FAILED'")).isEqualTo(1);

        jdbc.update("DELETE FROM sim_order");
        jdbc.update("DELETE FROM sim_access_grant");
        order("PRO", "awa@test.local");
        sync();
        jdbc.update("UPDATE sim_order SET created_at = NOW() - INTERVAL '3 days'");
        remote.set(null);
        orderService.reconcile();
        sync();
        assertThat(jdbc.queryForObject("SELECT status FROM sim_order", String.class)).isEqualTo("EXPIRED");
    }

    // ── Administration ───────────────────────────────────────────────

    @Test
    void ordersAndPlansAreForManagers_andPlanChangesAreAuditedAndNeverTouchOpenOrders() throws Exception {
        order("PRO", "awa@test.local");
        var manager = TestAuth.as("pedago@test.local", "PEDAGOGY");
        var other = TestAuth.as("formateur@test.local", "TRAINER");

        mockMvc.perform(get(ADMIN + "/orders").with(other)).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN + "/plans").with(other)).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN + "/payments/status").with(other)).andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN + "/plans/PRO").with(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pro\",\"priceXof\":50000,\"durationDays\":90,\"examAttempts\":5,\"active\":true}")).andExpect(status().isForbidden());

        // le profil Admissions voit les paiements : il voit aussi les commandes en ligne, mais ne peut ni les revérifier ni changer un prix
        var admissions = TestAuth.as("adm@test.local", "ADMISSIONS");
        mockMvc.perform(get(ADMIN + "/orders").with(admissions)).andExpect(status().isOk());
        mockMvc.perform(post(ADMIN + "/orders/" + java.util.UUID.randomUUID() + "/verify").with(admissions)).andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN + "/plans/PRO").with(admissions).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pro\",\"priceXof\":50000,\"durationDays\":90,\"examAttempts\":5,\"active\":true}")).andExpect(status().isForbidden());

        JsonNode list = json(mockMvc.perform(get(ADMIN + "/orders").with(manager)).andExpect(status().isOk()));
        assertThat(list.at("/data")).hasSize(1);
        assertThat(list.at("/data/0/learnerEmail").asText()).isEqualTo("awa@test.local");
        assertThat(list.at("/data/0/status").asText()).isEqualTo("PENDING");

        JsonNode gw = json(mockMvc.perform(get(ADMIN + "/payments/status").with(manager)).andExpect(status().isOk())).at("/data");
        assertThat(gw.at("/configured").asBoolean()).isTrue();
        assertThat(gw.at("/mode").asText()).isEqualTo("sandbox");
        assertThat(gw.at("/notificationUrl").asText()).endsWith("/api/v1/payments/paydunya/ipn");

        // changement de prix : validé, tracé, sans effet sur la commande déjà ouverte
        mockMvc.perform(put(ADMIN + "/plans/PRO").with(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pro\",\"priceXof\":50,\"durationDays\":90,\"examAttempts\":5,\"active\":true}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(ADMIN + "/plans/PRO").with(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pro\",\"priceXof\":50000,\"durationDays\":90,\"examAttempts\":5,\"active\":true}")).andExpect(status().isOk());
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("SIM_PLAN_UPDATED");
        sync();
        assertThat(count("SELECT amount_xof FROM sim_order")).isEqualTo(45000);
        assertThat(json(mockMvc.perform(get(API + "/plans"))).at("/data/plans/1/priceXof").asInt()).isEqualTo(50000);

        // un forfait retiré de la vente ne se commande plus ; le dernier forfait en vente ne peut pas être retiré
        mockMvc.perform(put(ADMIN + "/plans/ESSENTIEL").with(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Essentiel\",\"priceXof\":25000,\"durationDays\":30,\"examAttempts\":2,\"active\":false}")).andExpect(status().isOk());
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON).content(body("ESSENTIEL", "moussa@test.local"))).andExpect(status().isConflict());
        mockMvc.perform(put(ADMIN + "/plans/PRO").with(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pro\",\"priceXof\":50000,\"durationDays\":90,\"examAttempts\":5,\"active\":false}")).andExpect(status().isOk());
        mockMvc.perform(put(ADMIN + "/plans/PRO_COACHING").with(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pro + coaching\",\"priceXof\":75000,\"durationDays\":90,\"examAttempts\":5,\"active\":false}")).andExpect(status().isConflict());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
