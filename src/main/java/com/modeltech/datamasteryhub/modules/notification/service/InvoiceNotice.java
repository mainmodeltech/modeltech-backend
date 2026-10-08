package com.modeltech.datamasteryhub.modules.notification.service;

import java.time.LocalDate;

/** Données de l'e-mail « votre facture » (valeurs simples : envoi asynchrone hors transaction). */
public record InvoiceNotice(
        String to,
        String recipientName,
        String number,
        long total,
        String currency,
        LocalDate dueDate,
        byte[] pdf) {}
