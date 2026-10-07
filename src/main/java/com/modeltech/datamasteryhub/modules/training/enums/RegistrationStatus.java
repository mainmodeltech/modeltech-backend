package com.modeltech.datamasteryhub.modules.training.enums;

public enum RegistrationStatus {
    /** Candidature reçue, pas encore examinée. */
    PENDING,
    /** Acceptée : le lien de paiement est envoyé, on attend le paiement. */
    PAYMENT_PENDING,
    /** Le candidat a déclaré son paiement : l'équipe doit le vérifier. */
    PAYMENT_TO_CONFIRM,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    REJECTED
}
