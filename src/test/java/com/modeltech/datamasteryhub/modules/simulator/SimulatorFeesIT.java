package com.modeltech.datamasteryhub.modules.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.payments.GatewayHealth;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutRequest;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutSession;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayPayment;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.PaymentState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Frais répercutés (PAYMENTS_FEES_MODE=PASS_ON, 3,5 %) : seul le paiement en ligne les porte, et le prestataire doit confirmer le montant avec frais. */
@TestPropertySource(properties = {"app.payments.fees.mode=PASS_ON", "app.payments.fees.percent=3.5"})
class SimulatorFeesIT extends AbstractIntegrationTest {

    private static final String API = "/api/v1/simulator";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private GatewayHealth health;
    @MockBean private PaymentGateway gateway;

    private final AtomicReference<CheckoutRequest> sent = new AtomicReference<>();

    @BeforeEach
    void gateway() {
        health.reset();
        when(gateway.configured()).thenReturn(true);
        when(gateway.provider()).thenReturn("PAYDUNYA");
        when(gateway.mode()).thenReturn("sandbox");
        when(gateway.isGenuineNotificationHash("good")).thenReturn(true);
        when(gateway.createCheckout(any())).thenAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return new CheckoutSession("test_tok1", "https://pay.test/1");
        });
    }

    private ResultActions order(String channel) throws Exception {
        return mockMvc.perform(post(API + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PRO\"," + (channel == null ? "" : "\"channel\":\"" + channel + "\",") + "\"firstName\":\"Awa\",\"email\":\"awa@test.local\",\"acceptTerms\":true}"));
    }

    private void ipn() throws Exception {
        mockMvc.perform(post("/api/v1/payments/paydunya/ipn").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data[invoice][token]", "test_tok1").param("data[hash]", "good")).andExpect(status().isOk());
        em.flush();
        em.clear();
    }

    @Test
    void thePublicPlansShowThePriceAndTheOnlineTotal() throws Exception {
        JsonNode pro = objectMapper.readTree(mockMvc.perform(get(API + "/plans")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/data/plans/1");
        assertThat(pro.at("/priceXof").asInt()).isEqualTo(45000);
        assertThat(pro.at("/onlineFeeXof").asInt()).isEqualTo(1633);
        assertThat(pro.at("/onlineTotalXof").asInt()).isEqualTo(46633);
    }

    @Test
    void anOnlineOrderChargesThePriceAndTheFees_andThePaymentMustMatchThatExactAmount() throws Exception {
        order("ONLINE").andExpect(status().isCreated());
        assertThat(sent.get().amountXof()).isEqualTo(46633);
        assertThat(sent.get().description()).contains("1633");
        em.flush();
        em.clear();
        assertThat(jdbc.queryForMap("SELECT amount_xof, fee_xof, charged_xof FROM sim_order")).containsEntry("amount_xof", 45000).containsEntry("fee_xof", 1633).containsEntry("charged_xof", 46633);

        // le prestataire confirme le prix sans les frais : montant incohérent, rien n'est ouvert
        when(gateway.fetchStatus(anyString())).thenReturn(new GatewayPayment(PaymentState.COMPLETED, 45000, sent.get().orderRef(), "test", null, null));
        ipn();
        assertThat(jdbc.queryForObject("SELECT status FROM sim_order", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sim_access_grant", Integer.class)).isZero();
    }

    @Test
    void theExactAmountWithFeesOpensTheAccess() throws Exception {
        order("ONLINE").andExpect(status().isCreated());
        when(gateway.fetchStatus(anyString())).thenReturn(new GatewayPayment(PaymentState.COMPLETED, 46633, sent.get().orderRef(), "test", null, null));
        ipn();
        assertThat(jdbc.queryForObject("SELECT status FROM sim_order", String.class)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sim_access_grant", Integer.class)).isEqualTo(1);
    }

    @Test
    void aManualOrderStaysAtTheCataloguePrice() throws Exception {
        em.flush();
        jdbc.update("UPDATE payment_service_setting SET mode = 'ONLINE_AND_MANUAL' WHERE service = 'SIMULATOR'");
        em.clear();
        order("MANUAL").andExpect(status().isCreated());
        em.flush();
        em.clear();
        assertThat(jdbc.queryForMap("SELECT fee_xof, charged_xof FROM sim_order")).containsEntry("fee_xof", 0).containsEntry("charged_xof", 45000);
    }
}
