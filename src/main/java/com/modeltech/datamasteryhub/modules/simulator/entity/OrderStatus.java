package com.modeltech.datamasteryhub.modules.simulator.entity;

/** État d'une commande en ligne. Seul {@code PAID} ouvre un accès ; les autres sont définitifs (une nouvelle tentative = une nouvelle commande). */
public enum OrderStatus {
    PENDING, PAID, FAILED, CANCELLED, EXPIRED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
