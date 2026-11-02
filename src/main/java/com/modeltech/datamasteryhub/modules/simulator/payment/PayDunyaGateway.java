package com.modeltech.datamasteryhub.modules.simulator.payment;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptateur PayDunya (API « PAR » en HTTP/JSON, paiement par redirection).
 * <ul>
 *   <li>Création : {@code POST /checkout-invoice/create} → jeton + adresse de paiement (champ {@code response_text}).</li>
 *   <li>Vérification : {@code GET /checkout-invoice/confirm/{jeton}} → statut, montant, données libres.</li>
 * </ul>
 * Les clés (maître, privée, jeton) viennent de l'environnement, jamais du code ni des journaux. Mode « sandbox » = aucun argent réel.
 */
@Component
@Slf4j
public class PayDunyaGateway implements PaymentGateway {

    static final String SANDBOX_URL = "https://app.paydunya.com/sandbox-api/v1";
    static final String LIVE_URL = "https://app.paydunya.com/api/v1";

    private final String mode;
    private final String masterKey;
    private final String privateKey;
    private final String token;
    private final String storeName;
    private final String callbackUrl;
    private final RestClient http;
    /** Vrai seulement si une adresse de test locale est configurée (faux serveur) : la page de paiement peut alors être en http://localhost. */
    private final boolean localCheckoutAllowed;

    @org.springframework.beans.factory.annotation.Autowired
    public PayDunyaGateway(@Value("${app.paydunya.mode:sandbox}") String mode,
                           @Value("${app.paydunya.master-key:}") String masterKey,
                           @Value("${app.paydunya.private-key:}") String privateKey,
                           @Value("${app.paydunya.token:}") String token,
                           @Value("${app.paydunya.store-name:Model Technologie}") String storeName,
                           @Value("${app.paydunya.callback-url:}") String callbackUrl,
                           @Value("${app.paydunya.base-url:}") String baseUrlOverride) {
        this(mode, masterKey, privateKey, token, storeName, callbackUrl, restClient(mode, baseUrlOverride, null),
                baseUrlOverride != null && baseUrlOverride.trim().startsWith("http://localhost"));
    }

    /** Pour les tests : le client HTTP est fourni (serveur simulé). */
    PayDunyaGateway(String mode, String masterKey, String privateKey, String token, String storeName, String callbackUrl, RestClient http) {
        this(mode, masterKey, privateKey, token, storeName, callbackUrl, http, false);
    }

    private PayDunyaGateway(String mode, String masterKey, String privateKey, String token, String storeName, String callbackUrl, RestClient http,
                            boolean localCheckoutAllowed) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!m.equals("sandbox") && !m.equals("live")) {
            throw new IllegalStateException("app.paydunya.mode doit valoir « sandbox » ou « live » (reçu : « " + mode + " »).");
        }
        this.mode = m;
        this.masterKey = masterKey == null ? "" : masterKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.token = token == null ? "" : token.trim();
        this.storeName = storeName;
        this.callbackUrl = callbackUrl == null ? "" : callbackUrl.trim();
        this.http = http;
        this.localCheckoutAllowed = localCheckoutAllowed;
        if (configured() && m.equals("sandbox")) log.warn("PayDunya en mode SANDBOX : aucun paiement réel n'est possible.");
    }

    static RestClient restClient(String mode, String baseUrlOverride, RestClient.Builder builder) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        String base = baseUrlOverride != null && !baseUrlOverride.isBlank() ? baseUrlOverride.trim() : (m.equals("live") ? LIVE_URL : SANDBOX_URL);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(12));
        RestClient.Builder b = builder != null ? builder : RestClient.builder().requestFactory(factory);
        return b.baseUrl(base).build();
    }

    @Override
    public String provider() {
        return "PAYDUNYA";
    }

    @Override
    public String mode() {
        return mode;
    }

    @Override
    public boolean configured() {
        return !masterKey.isEmpty() && !privateKey.isEmpty() && !token.isEmpty();
    }

    @Override
    public String notificationUrl() {
        return callbackUrl;
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest r) {
        requireConfigured();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", r.itemName());
        item.put("quantity", 1);
        item.put("unit_price", r.amountXof());
        item.put("total_price", r.amountXof());
        item.put("description", r.description());

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", r.customerName());
        customer.put("email", r.customerEmail());
        if (r.customerPhone() != null && !r.customerPhone().isBlank()) customer.put("phone", r.customerPhone());

        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("total_amount", r.amountXof());
        invoice.put("description", r.description());
        invoice.put("items", Map.of("item_0", item));
        invoice.put("customer", customer);

        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("return_url", r.returnUrl());
        actions.put("cancel_url", r.cancelUrl());
        if (!callbackUrl.isEmpty()) actions.put("callback_url", callbackUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoice", invoice);
        body.put("store", Map.of("name", storeName));
        body.put("custom_data", Map.of("order_ref", r.orderRef()));
        body.put("actions", actions);

        JsonNode response = call(() -> http.post().uri("/checkout-invoice/create").headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
        String code = text(response, "response_code");
        String tokenValue = text(response, "token");
        String url = text(response, "response_text");
        boolean safeUrl = url.startsWith("https://") || (localCheckoutAllowed && url.startsWith("http://localhost"));
        if (!"00".equals(code) || tokenValue.isEmpty() || !safeUrl) {
            log.error("PayDunya a refusé la création de la facture (code {}, message « {} »)", code, text(response, "response_text"));
            throw new GatewayException("Le prestataire de paiement a refusé la demande.");
        }
        return new CheckoutSession(tokenValue, url);
    }

    @Override
    public GatewayPayment fetchStatus(String providerToken) {
        requireConfigured();
        if (providerToken == null || !providerToken.matches("[A-Za-z0-9_-]{4,120}")) {
            return new GatewayPayment(PaymentState.NOT_FOUND, 0, null, null, null, null);
        }
        JsonNode response = call(() -> http.get().uri("/checkout-invoice/confirm/{token}", providerToken).headers(this::auth)
                .retrieve().body(JsonNode.class));
        if (!"00".equals(text(response, "response_code"))) {
            return new GatewayPayment(PaymentState.NOT_FOUND, 0, null, null, null, null);
        }
        PaymentState state = switch (text(response, "status").toLowerCase(Locale.ROOT)) {
            case "completed" -> PaymentState.COMPLETED;
            case "cancelled", "canceled" -> PaymentState.CANCELLED;
            case "failed" -> PaymentState.FAILED;
            default -> PaymentState.PENDING;
        };
        String orderRef = text(response.path("custom_data"), "order_ref");
        String receipt = text(response, "receipt_url");
        String failReason = text(response, "fail_reason");
        return new GatewayPayment(state, amount(response.path("invoice").path("total_amount")), orderRef.isEmpty() ? null : orderRef,
                text(response, "mode").isEmpty() ? null : text(response, "mode"), receipt.isEmpty() ? null : receipt,
                failReason.isEmpty() ? null : failReason);
    }

    @Override
    public boolean isGenuineNotificationHash(String hash) {
        if (hash == null || hash.isBlank() || masterKey.isEmpty()) return false;
        return MessageDigest.isEqual(sha512(masterKey).getBytes(StandardCharsets.UTF_8), hash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    // ── Privé ───────────────────────────────────────────────────────

    private void requireConfigured() {
        if (!configured()) throw new GatewayException("Le paiement en ligne n'est pas configuré.");
    }

    private void auth(org.springframework.http.HttpHeaders h) {
        h.set("PAYDUNYA-MASTER-KEY", masterKey);
        h.set("PAYDUNYA-PRIVATE-KEY", privateKey);
        h.set("PAYDUNYA-TOKEN", token);
    }

    private JsonNode call(java.util.function.Supplier<JsonNode> request) {
        try {
            JsonNode body = request.get();
            if (body == null || body.isMissingNode()) throw new GatewayException("Réponse vide du prestataire de paiement.");
            return body;
        } catch (RestClientException e) {
            // Le message d'une exception HTTP peut contenir l'adresse et des en-têtes : on ne journalise que sa nature.
            log.error("Appel PayDunya impossible : {}", e.getClass().getSimpleName());
            throw new GatewayException("Le prestataire de paiement est momentanément injoignable.", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static long amount(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return 0;
        if (node.isNumber()) return node.decimalValue().longValue();
        try {
            return new BigDecimal(node.asText("0").trim()).longValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String sha512(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
