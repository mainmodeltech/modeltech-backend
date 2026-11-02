package com.modeltech.datamasteryhub.modules.simulator.payment;

/**
 * Passerelle de paiement en ligne (patron Adapter) : le domaine du simulateur ne connaît que ce contrat, jamais le fournisseur.
 * Changer de prestataire = une nouvelle implémentation, aucune modification des commandes.
 */
public interface PaymentGateway {

    /** Nom stocké avec chaque commande (ex. « PAYDUNYA »). */
    String provider();

    /** « sandbox » (essais, aucun argent réel) ou « live ». */
    String mode();

    /** Faux tant que les clés ne sont pas renseignées : l'achat en ligne est alors désactivé, sans erreur. */
    boolean configured();

    /** Adresse de notification (IPN) déclarée au prestataire, pour l'afficher à l'administration. Vide si non définie. */
    String notificationUrl();

    CheckoutSession createCheckout(CheckoutRequest request);

    /** Interroge le prestataire sur l'état d'un paiement : seule source de vérité (jamais le corps d'une notification reçue). */
    GatewayPayment fetchStatus(String providerToken);

    /** Vrai si le « hash » joint à une notification est celui que le prestataire doit envoyer (défense en profondeur). */
    boolean isGenuineNotificationHash(String hash);

    record CheckoutRequest(String orderRef, String itemName, String description, long amountXof, String customerName, String customerEmail,
                           String customerPhone, String returnUrl, String cancelUrl) {}

    record CheckoutSession(String token, String url) {}

    enum PaymentState { PENDING, COMPLETED, CANCELLED, FAILED, NOT_FOUND }

    /** @param orderRef référence de commande renvoyée par le prestataire (donnée libre transmise à la création) ; {@code null} si absente */
    record GatewayPayment(PaymentState state, long totalAmount, String orderRef, String mode, String receiptUrl, String failReason) {}

    /** Échec technique ou refus du prestataire : message sans secret, affichable. */
    class GatewayException extends RuntimeException {
        public GatewayException(String message) {
            super(message);
        }

        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
