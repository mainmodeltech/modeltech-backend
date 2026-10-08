package com.modeltech.datamasteryhub.modules.training.enums;

public enum PaymentStatus {
    /** Échéance due, rien reçu. */
    PENDING,
    /** Le candidat (ou l'admin pour lui) a déclaré avoir payé : à vérifier. */
    DECLARED,
    CONFIRMED,
    CANCELLED,
    /** Remboursé (le remboursement se fait hors plateforme, il est consigné ici). */
    REFUNDED
}
