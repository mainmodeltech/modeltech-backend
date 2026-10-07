package com.modeltech.datamasteryhub.modules.notification.service;

import java.time.LocalDate;

/**
 * Données d'une notification de paiement. Valeurs simples (pas d'entités) : les envois sont
 * asynchrones, hors de la transaction qui a chargé les relations paresseuses.
 *
 * @param link lien de paiement public du candidat
 */
public record PaymentNotice(
        String to,
        String firstName,
        String lastName,
        String bootcampTitle,
        String sessionName,
        long amount,
        String currency,
        int installmentNumber,
        int installmentCount,
        LocalDate dueDate,
        String link,
        String method,
        String reference,
        String reason) {

    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}
