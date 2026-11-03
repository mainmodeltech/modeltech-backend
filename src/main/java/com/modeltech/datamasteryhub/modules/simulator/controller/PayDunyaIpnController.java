package com.modeltech.datamasteryhub.modules.simulator.controller;

import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.coaching.service.CoachingPaymentService;
import com.modeltech.datamasteryhub.modules.simulator.service.OrderService;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification de paiement instantanée (IPN) de PayDunya : {@code POST application/x-www-form-urlencoded}, champs imbriqués sous
 * {@code data} (ex. {@code data[invoice][token]}, {@code data[status]}, {@code data[hash]}).
 *
 * <p>Route publique par nature (PayDunya l'appelle depuis ses serveurs), donc <b>rien de ce qui est reçu n'est cru</b> : le jeton
 * sert uniquement à retrouver la commande, dont l'état est ensuite relu auprès de PayDunya avec nos clés. Un jeton inconnu est
 * ignoré. Le « hash » (SHA-512 de la clé maître) n'est qu'un contrôle supplémentaire. Réponse toujours 200, sans détail, pour ne pas
 * déclencher de relances en rafale ni informer un tiers.
 */
@RestController
@RequestMapping("/api/v1/payments/paydunya")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Paiement (Public)", description = "Notification du prestataire de paiement")
public class PayDunyaIpnController {

    private final OrderService orders;
    private final RegistrationCheckoutService registrationCheckouts;
    private final CoachingPaymentService coachingPayments;
    private final PaymentGateway gateway;

    @PostMapping(value = "/ipn", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE})
    @Operation(summary = "Notification de paiement PayDunya (IPN)")
    public ResponseEntity<String> ipn(HttpServletRequest request) {
        String token = request.getParameter("data[invoice][token]");
        String hash = request.getParameter("data[hash]");
        if (token == null || token.isBlank()) {
            log.warn("Notification PayDunya sans jeton de facture (ignorée)");
        } else if (hash != null && !hash.isBlank() && !gateway.isGenuineNotificationHash(hash)) {
            // Seul le prestataire connaît la clé maître : un hash différent n'est pas de lui. Le rapprochement rattrapera un vrai paiement.
            log.warn("Notification PayDunya avec un hash inattendu (ignorée)");
        } else {
            try {
                if (!orders.onNotification(token) && !registrationCheckouts.onNotification(token) && !coachingPayments.onNotification(token)) {
                    log.warn("Notification de paiement pour un jeton inconnu (ignorée)");
                }
            } catch (RuntimeException e) {
                log.error("Traitement de la notification PayDunya en échec : {}", e.getMessage());
            }
        }
        return ResponseEntity.ok("OK");
    }
}
