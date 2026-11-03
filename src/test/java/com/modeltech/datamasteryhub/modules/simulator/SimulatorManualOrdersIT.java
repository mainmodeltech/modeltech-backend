package com.modeltech.datamasteryhub.modules.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.payments.GatewayHealth;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.simulator.service.OrderService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paiement manuel d'une commande du simulateur (Wave, Orange Money, virement) : déclaration, justificatif, confirmation ou refus par
 * l'équipe. L'accès ne s'ouvre qu'à la confirmation ; la passerelle n'est jamais sollicitée.
 */
class SimulatorManualOrdersIT extends AbstractIntegrationTest {

    private static final String API = "/api/v1/simulator";
    private static final String ADMIN = "/api/v1/admin/simulator";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private OrderService orderService;
    @Autowired private AuditRepository audit;
    @Autowired private GatewayHealth health;
    @MockBean private PaymentGateway gateway;
    @MockBean private StorageService storage;

    @BeforeEach
    void manualOnly() {
        health.reset();
        when(gateway.configured()).thenReturn(true);
        when(gateway.provider()).thenReturn("PAYDUNYA");
        when(gateway.mode()).thenReturn("sandbox");
        when(storage.uploadPrivateImage(any(), eq("sim-order-proofs"))).thenReturn(new StorageService.UploadResult("sim-order-proofs/p1.png", null));
        when(storage.presignedGetUrl(eq("sim-order-proofs/p1.png"), anyInt())).thenReturn("https://signed.test/p1.png");
        setMode("MANUAL_ONLY");
    }

    private void setMode(String mode) {
        em.flush();
        jdbc.update("UPDATE payment_service_setting SET mode = ? WHERE service = 'SIMULATOR'", mode);
        em.clear();
    }

    private void sync() {
        em.flush();
        em.clear();
    }

    private static RequestPostProcessor finance() {
        return TestAuth.as("fin@test.local", "FINANCE");
    }

    private JsonNode order(String email) throws Exception {
        return json(mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PRO\",\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"" + email + "\",\"acceptTerms\":true}"))
                .andExpect(status().isCreated())).at("/data");
    }

    private ResultActions declare(String ref, String body) throws Exception {
        return mockMvc.perform(post(API + "/orders/" + ref + "/declare").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String orderId() {
        return jdbc.queryForObject("SELECT id::text FROM sim_order LIMIT 1", String.class);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void aManualOrderNeverTouchesTheGateway_andPaysTheCataloguePrice() throws Exception {
        JsonNode created = order("awa@test.local");
        assertThat(created.at("/channel").asText()).isEqualTo("MANUAL");
        assertThat(created.at("/checkoutUrl").isNull()).isTrue();
        verify(gateway, never()).createCheckout(any());
        sync();
        var row = jdbc.queryForMap("SELECT status, provider, charged_xof, fee_xof, channel FROM sim_order");
        assertThat(row).containsEntry("status", "PENDING").containsEntry("provider", "MANUAL").containsEntry("charged_xof", 45000)
                .containsEntry("fee_xof", 0).containsEntry("channel", "MANUAL");
        JsonNode status = json(mockMvc.perform(get(API + "/orders/" + created.at("/orderRef").asText())).andExpect(status().isOk())).at("/data");
        assertThat(status.at("/channel").asText()).isEqualTo("MANUAL");
        assertThat(status.at("/status").asText()).isEqualTo("PENDING");
        assertThat(status.at("/chargedXof").asInt()).isEqualTo(45000);
        verify(gateway, never()).fetchStatus(any());
    }

    @Test
    void theBuyerDeclaresThePayment_attachesAProof_andNothingIsOpenedUntilTheTeamConfirms() throws Exception {
        String ref = order("awa@test.local").at("/orderRef").asText();
        declare(ref, "{\"method\":\"WAVE\"}").andExpect(status().isBadRequest());                         // référence obligatoire
        declare(ref, "{\"method\":\"BITCOIN\",\"reference\":\"x\"}").andExpect(status().isBadRequest());   // moyen inconnu
        JsonNode declared = json(declare(ref, "{\"method\":\"WAVE\",\"reference\":\"WV-123456\"}").andExpect(status().isOk())).at("/data");
        assertThat(declared.at("/status").asText()).isEqualTo("DECLARED");
        assertThat(declared.at("/declaredMethod").asText()).isEqualTo("WAVE");
        declare(ref, "{\"method\":\"WAVE\",\"reference\":\"WV-123456\"}").andExpect(status().isConflict());   // déjà déclaré

        MockMultipartFile proof = new MockMultipartFile("file", "recu.png", "image/png", new byte[]{1, 2, 3});
        JsonNode withProof = json(mockMvc.perform(multipart(API + "/orders/" + ref + "/proof").file(proof)).andExpect(status().isOk())).at("/data");
        assertThat(withProof.at("/proofUploaded").asBoolean()).isTrue();
        sync();
        assertThat(count("SELECT count(*) FROM sim_access_grant")).isZero();          // rien d'ouvert sans confirmation
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("SIM_ORDER_DECLARED");
        verify(gateway, never()).createCheckout(any());
    }

    @Test
    void theTeamConfirms_opensExactlyOneAccess_andOnlyAuthorisedProfilesCan() throws Exception {
        String ref = order("awa@test.local").at("/orderRef").asText();
        declare(ref, "{\"method\":\"ORANGE_MONEY\",\"reference\":\"OM-987\"}").andExpect(status().isOk());
        sync();
        String id = orderId();

        mockMvc.perform(post(ADMIN + "/orders/" + id + "/confirm").with(TestAuth.as("formateur@test.local", "TRAINER"))).andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN + "/orders/" + id + "/confirm")).andExpect(status().isUnauthorized());

        JsonNode confirmed = json(mockMvc.perform(post(ADMIN + "/orders/" + id + "/confirm").with(finance())).andExpect(status().isOk())).at("/data");
        assertThat(confirmed.at("/status").asText()).isEqualTo("PAID");
        assertThat(confirmed.at("/confirmedBy").asText()).isEqualTo("fin@test.local");
        mockMvc.perform(post(ADMIN + "/orders/" + id + "/confirm").with(finance())).andExpect(status().isConflict());   // une seule fois

        sync();
        var grant = jdbc.queryForMap("SELECT plan_code, exam_attempts_total, granted_by FROM sim_access_grant");
        assertThat(grant).containsEntry("plan_code", "PRO").containsEntry("exam_attempts_total", 5).containsEntry("granted_by", "paiement-manuel");
        assertThat(count("SELECT count(*) FROM sim_access_grant")).isEqualTo(1);
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("SIM_ORDER_PAID");
        JsonNode catalog = json(mockMvc.perform(get("/api/v1/learner/simulator/catalog").with(TestAuth.as("awa@test.local", "LEARNER"))).andExpect(status().isOk())).at("/data");
        assertThat(catalog.at("/access/0/planCode").asText()).isEqualTo("PRO");
    }

    @Test
    void aRejectedPaymentGoesBackToPending_withTheReasonShownToTheBuyer_whoCanDeclareAgain() throws Exception {
        String ref = order("awa@test.local").at("/orderRef").asText();
        sync();
        String id = orderId();
        mockMvc.perform(post(ADMIN + "/orders/" + id + "/reject").with(finance()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict());                                     // rien à refuser : rien n'a été déclaré

        declare(ref, "{\"method\":\"VIREMENT\",\"reference\":\"VIR-1\"}").andExpect(status().isOk());
        sync();
        mockMvc.perform(post(ADMIN + "/orders/" + id + "/reject").with(finance()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN + "/orders/" + id + "/reject").with(finance()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Montant reçu inférieur au prix du forfait\"}")).andExpect(status().isOk());

        JsonNode back = json(mockMvc.perform(get(API + "/orders/" + ref)).andExpect(status().isOk())).at("/data");
        assertThat(back.at("/status").asText()).isEqualTo("PENDING");
        assertThat(back.at("/note").asText()).contains("Montant reçu inférieur");
        assertThat(back.at("/declaredMethod").isNull()).isTrue();
        declare(ref, "{\"method\":\"WAVE\",\"reference\":\"WV-2\"}").andExpect(status().isOk());
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("SIM_ORDER_REJECTED");
    }

    @Test
    void theManualRoutesRefuseAnOnlineOrder_andTheManualChoiceRefusesWhenItIsNotOffered() throws Exception {
        setMode("ONLINE_AND_MANUAL");
        when(gateway.createCheckout(any())).thenReturn(new PaymentGateway.CheckoutSession("test_tok1", "https://pay.test/1"));
        JsonNode online = json(mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PRO\",\"channel\":\"ONLINE\",\"firstName\":\"Awa\",\"email\":\"awa@test.local\",\"acceptTerms\":true}")).andExpect(status().isCreated())).at("/data");
        declare(online.at("/orderRef").asText(), "{\"method\":\"WAVE\",\"reference\":\"x\"}").andExpect(status().isConflict());

        setMode("ONLINE_ONLY");        // en ligne seulement et la passerelle répond : le manuel n'est pas proposé
        mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PRO\",\"channel\":\"MANUAL\",\"firstName\":\"Moussa\",\"email\":\"moussa@test.local\",\"acceptTerms\":true}")).andExpect(status().isConflict());
    }

    @Test
    void theSweepAbandonsNeverPaidManualOrders_butNeverADeclaredOne_andNeverCallsTheGateway() throws Exception {
        String ref1 = order("awa@test.local").at("/orderRef").asText();
        String ref2 = order("moussa@test.local").at("/orderRef").asText();
        declare(ref2, "{\"method\":\"WAVE\",\"reference\":\"WV-9\"}").andExpect(status().isOk());
        sync();
        jdbc.update("UPDATE sim_order SET created_at = NOW() - INTERVAL '8 days'");
        orderService.reconcile();
        sync();
        assertThat(jdbc.queryForObject("SELECT status FROM sim_order WHERE public_ref = ?::uuid", String.class, ref1)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM sim_order WHERE public_ref = ?::uuid", String.class, ref2)).isEqualTo("DECLARED");
        verify(gateway, never()).fetchStatus(any());
    }

    @Test
    void theProofIsAPrivateSignedLinkForTheTeamOnly() throws Exception {
        String ref = order("awa@test.local").at("/orderRef").asText();
        sync();
        String id = orderId();
        mockMvc.perform(get(ADMIN + "/orders/" + id + "/proof").with(finance())).andExpect(status().isNotFound());       // pas encore de justificatif
        mockMvc.perform(multipart(API + "/orders/" + ref + "/proof").file(new MockMultipartFile("file", "r.png", "image/png", new byte[]{1}))).andExpect(status().isOk());
        sync();
        assertThat(json(mockMvc.perform(get(ADMIN + "/orders/" + id + "/proof").with(finance())).andExpect(status().isOk())).at("/data/url").asText()).isEqualTo("https://signed.test/p1.png");
        mockMvc.perform(get(ADMIN + "/orders/" + id + "/proof").with(TestAuth.as("formateur@test.local", "TRAINER"))).andExpect(status().isForbidden());
        // jamais d'adresse publique du justificatif dans le suivi de l'acheteur
        assertThat(mockMvc.perform(get(API + "/orders/" + ref)).andReturn().getResponse().getContentAsString()).doesNotContain("p1.png");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
