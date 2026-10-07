package com.modeltech.datamasteryhub.modules.training.enums;

/** Moyen de paiement. Seuls WAVE, ORANGE_MONEY et VIREMENT sont déclarables par le candidat. */
public enum PaymentMethod {
    WAVE,
    ORANGE_MONEY,
    VIREMENT,
    /** Facture / bon de commande d'une entreprise (saisi par l'admin). */
    ENTREPRISE,
    ESPECES
}
