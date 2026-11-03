package com.modeltech.datamasteryhub.modules.simulator.entity;

/**
 * État d'une commande. Seul {@code PAID} ouvre un accès.
 * {@code PENDING} : en attente de paiement ; {@code DECLARED} : paiement manuel déclaré par l'acheteur, en attente de confirmation par l'équipe ;
 * les autres sont définitifs (une nouvelle tentative = une nouvelle commande).
 */
public enum OrderStatus {
    PENDING, DECLARED, PAID, FAILED, CANCELLED, EXPIRED;

    public boolean isFinal() {
        return this != PENDING && this != DECLARED;
    }
}
