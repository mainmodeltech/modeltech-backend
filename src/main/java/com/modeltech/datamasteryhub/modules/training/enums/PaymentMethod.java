package com.modeltech.datamasteryhub.modules.training.enums;

/** Moyen de paiement. Seuls WAVE, ORANGE_MONEY et VIREMENT sont déclarables par le candidat ; PAYDUNYA est posé par le serveur. */
public enum PaymentMethod {
    WAVE,
    ORANGE_MONEY,
    VIREMENT,
    /** Facture / bon de commande d'une entreprise (saisi par l'admin). */
    ENTREPRISE,
    ESPECES,
    /** Payé en ligne par la passerelle (confirmé automatiquement après relecture chez le prestataire). Jamais déclarable par le candidat. */
    PAYDUNYA
}
