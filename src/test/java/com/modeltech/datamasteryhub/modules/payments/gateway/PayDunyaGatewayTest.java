package com.modeltech.datamasteryhub.modules.payments.gateway;

import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutRequest;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutSession;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayException;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayPayment;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Adaptateur PayDunya contre un serveur simulé : requêtes envoyées, réponses interprétées, aucune fuite de clé (sans Spring, sans réseau). */
class PayDunyaGatewayTest {

    private static final String BASE = PayDunyaGateway.SANDBOX_URL;
    private static final String MASTER = "master-key-secret-value";

    private MockRestServiceServer server;
    private PayDunyaGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new PayDunyaGateway("sandbox", MASTER, "private-key-secret-value", "token-secret-value", "Model Technologie",
                "https://api-staging.model-technologie.com/api/v1/payments/paydunya/ipn", PayDunyaGateway.restClient("sandbox", null, builder));
    }

    private static CheckoutRequest request() {
        return new CheckoutRequest("11111111-2222-3333-4444-555555555555", "Simulateur PL-300 — Pro", "Accès au simulateur", 45000, "Awa Diop",
                "awa@example.com", "+221770000000", "https://site.test/retour", "https://site.test/annule");
    }

    @Test
    void createCheckout_sendsTheInvoiceWithAuthenticationHeaders_andReturnsTheTokenAndUrl() {
        server.expect(requestTo(BASE + "/checkout-invoice/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PAYDUNYA-MASTER-KEY", MASTER))
                .andExpect(header("PAYDUNYA-PRIVATE-KEY", "private-key-secret-value"))
                .andExpect(header("PAYDUNYA-TOKEN", "token-secret-value"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.invoice.total_amount").value(45000))
                .andExpect(jsonPath("$.invoice.items.item_0.quantity").value(1))
                .andExpect(jsonPath("$.invoice.customer.email").value("awa@example.com"))
                .andExpect(jsonPath("$.store.name").value("Model Technologie"))
                .andExpect(jsonPath("$.custom_data.order_ref").value("11111111-2222-3333-4444-555555555555"))
                .andExpect(jsonPath("$.actions.return_url").value("https://site.test/retour"))
                .andExpect(jsonPath("$.actions.cancel_url").value("https://site.test/annule"))
                .andExpect(jsonPath("$.actions.callback_url").value("https://api-staging.model-technologie.com/api/v1/payments/paydunya/ipn"))
                .andRespond(withSuccess("{\"response_code\":\"00\",\"response_text\":\"https://app.paydunya.com/sandbox-checkout/invoice/test_abc\","
                        + "\"description\":\"Checkout Invoice Created\",\"token\":\"test_abc\"}", MediaType.APPLICATION_JSON));
        CheckoutSession session = gateway.createCheckout(request());
        assertThat(session.token()).isEqualTo("test_abc");
        assertThat(session.url()).isEqualTo("https://app.paydunya.com/sandbox-checkout/invoice/test_abc");
        server.verify();
    }

    @Test
    void createCheckout_refusedByTheProvider_orUnreachable_failsWithoutLeakingAnySecret() {
        server.expect(requestTo(BASE + "/checkout-invoice/create"))
                .andRespond(withSuccess("{\"response_code\":\"1001\",\"response_text\":\"Invalid keys\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.createCheckout(request())).isInstanceOf(GatewayException.class).hasMessageContaining("refusé");

        server.reset();
        server.expect(requestTo(BASE + "/checkout-invoice/create")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.createCheckout(request())).isInstanceOf(GatewayException.class)
                .hasMessageContaining("injoignable")
                .satisfies(e -> assertThat(e.getMessage() + e.getCause()).doesNotContain(MASTER).doesNotContain("private-key-secret-value").doesNotContain("token-secret-value"));
    }

    @Test
    void createCheckout_refusesAPaymentPageThatIsNotHttps() {
        server.expect(requestTo(BASE + "/checkout-invoice/create"))
                .andRespond(withSuccess("{\"response_code\":\"00\",\"response_text\":\"http://evil.test/pay\",\"token\":\"test_x\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.createCheckout(request())).isInstanceOf(GatewayException.class);
    }

    @Test
    void fetchStatus_readsTheStatusTheAmountAndTheOrderReference() {
        server.expect(requestTo(BASE + "/checkout-invoice/confirm/test_abc"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PAYDUNYA-MASTER-KEY", MASTER))
                .andRespond(withSuccess("{\"response_code\":\"00\",\"response_text\":\"Transaction Found\",\"status\":\"completed\",\"mode\":\"test\","
                        + "\"invoice\":{\"token\":\"test_abc\",\"total_amount\":\"45000.00\"},\"custom_data\":{\"order_ref\":\"11111111-2222-3333-4444-555555555555\"},"
                        + "\"receipt_url\":\"https://paydunya.com/receipt/pdf/abc.pdf\"}", MediaType.APPLICATION_JSON));
        GatewayPayment p = gateway.fetchStatus("test_abc");
        assertThat(p.state()).isEqualTo(PaymentState.COMPLETED);
        assertThat(p.totalAmount()).isEqualTo(45000);
        assertThat(p.orderRef()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(p.mode()).isEqualTo("test");
        assertThat(p.receiptUrl()).isEqualTo("https://paydunya.com/receipt/pdf/abc.pdf");
    }

    @Test
    void fetchStatus_mapsEveryStatus_andTreatsAnUnknownOrMalformedTokenAsNotFound() {
        for (String[] c : new String[][]{{"pending", "PENDING"}, {"cancelled", "CANCELLED"}, {"failed", "FAILED"}, {"something-new", "PENDING"}}) {
            server.reset();
            server.expect(requestTo(BASE + "/checkout-invoice/confirm/test_s"))
                    .andRespond(withSuccess("{\"response_code\":\"00\",\"status\":\"" + c[0] + "\",\"invoice\":{\"total_amount\":45000}}", MediaType.APPLICATION_JSON));
            assertThat(gateway.fetchStatus("test_s").state()).as(c[0]).isEqualTo(PaymentState.valueOf(c[1]));
        }
        server.reset();
        server.expect(requestTo(BASE + "/checkout-invoice/confirm/test_missing"))
                .andRespond(withSuccess("{\"response_code\":\"1002\",\"response_text\":\"Invoice Not Found\"}", MediaType.APPLICATION_JSON));
        assertThat(gateway.fetchStatus("test_missing").state()).isEqualTo(PaymentState.NOT_FOUND);

        server.reset();   // un jeton qui ne ressemble pas à un jeton ne part même pas vers le prestataire (pas d'injection de chemin)
        assertThat(gateway.fetchStatus("../../admin?x=1").state()).isEqualTo(PaymentState.NOT_FOUND);
        server.verify();
    }

    @Test
    void notificationHash_isTheSha512OfTheMasterKey_andNothingElseIsAccepted() {
        assertThat(gateway.isGenuineNotificationHash(PayDunyaGateway.sha512(MASTER))).isTrue();
        assertThat(gateway.isGenuineNotificationHash(PayDunyaGateway.sha512(MASTER).toUpperCase())).isTrue();
        assertThat(gateway.isGenuineNotificationHash(PayDunyaGateway.sha512("autre"))).isFalse();
        assertThat(gateway.isGenuineNotificationHash("")).isFalse();
        assertThat(gateway.isGenuineNotificationHash(null)).isFalse();
    }

    @Test
    void withoutKeys_theGatewayIsDisabled_andAWrongModeStopsTheStartup() {
        PayDunyaGateway empty = new PayDunyaGateway("sandbox", "", "", "", "Model Technologie", "", PayDunyaGateway.restClient("sandbox", null, RestClient.builder()));
        assertThat(empty.configured()).isFalse();
        assertThatThrownBy(() -> empty.createCheckout(request())).isInstanceOf(GatewayException.class).hasMessageContaining("configuré");
        assertThat(gateway.configured()).isTrue();
        assertThat(gateway.mode()).isEqualTo("sandbox");
        assertThatThrownBy(() -> new PayDunyaGateway("prod", "a", "b", "c", "x", "", PayDunyaGateway.restClient("sandbox", null, RestClient.builder())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void liveModeUsesTheProductionEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer live = MockRestServiceServer.bindTo(builder).build();
        PayDunyaGateway g = new PayDunyaGateway("live", "m", "p", "t", "Model Technologie", "", PayDunyaGateway.restClient("live", null, builder));
        live.expect(requestTo(PayDunyaGateway.LIVE_URL + "/checkout-invoice/confirm/live_abc"))
                .andRespond(withSuccess("{\"response_code\":\"00\",\"status\":\"pending\",\"invoice\":{\"total_amount\":100}}", MediaType.APPLICATION_JSON));
        assertThat(g.fetchStatus("live_abc").state()).isEqualTo(PaymentState.PENDING);
        live.verify();
    }
}
