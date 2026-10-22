package com.modeltech.datamasteryhub.modules.course.enums;

/** Cycle d'une proposition de modification du programme. Les trois premiers états sont « en cours » (une seule par formation et par auteur). */
public enum ProposalStatus {
    /** En préparation par le formateur. */
    DRAFT,
    /** Soumise : en attente de relecture par la pédagogie (plus modifiable, sauf à la retirer). */
    SUBMITTED,
    /** Renvoyée au formateur avec un commentaire : il la corrige puis la soumet de nouveau. */
    CHANGES_REQUESTED,
    /** Validée : fusionnée dans le programme en ligne. */
    APPROVED,
    /** Refusée (avec un commentaire). */
    REJECTED
}
