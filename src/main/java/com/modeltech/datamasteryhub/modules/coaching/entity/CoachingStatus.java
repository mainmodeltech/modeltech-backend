package com.modeltech.datamasteryhub.modules.coaching.entity;

/**
 * Cycle d'une demande de coaching :
 * <pre>
 * NEW --prise en charge--> IN_REVIEW --validation (formateur confirmé, offre, montant)--> AWAITING_PAYMENT --paiement--> PAID
 *   \-- refus --> DECLINED                                  \--déclaration manuelle--> PAYMENT_DECLARED --confirmation--> PAID
 *                                        AWAITING_PAYMENT / PAYMENT_DECLARED --annulation--> CANCELLED ; lien non payé à temps --> EXPIRED
 * </pre>
 */
public enum CoachingStatus {
    NEW,
    IN_REVIEW,
    AWAITING_PAYMENT,
    PAYMENT_DECLARED,
    PAID,
    DECLINED,
    CANCELLED,
    EXPIRED;

    /** Le lien de paiement est actif : le client peut payer. */
    public boolean acceptsPayment() {
        return this == AWAITING_PAYMENT || this == PAYMENT_DECLARED;
    }

    /** Demande encore en cours de traitement par l'équipe (avant validation). */
    public boolean isOpenForReview() {
        return this == NEW || this == IN_REVIEW;
    }
}
