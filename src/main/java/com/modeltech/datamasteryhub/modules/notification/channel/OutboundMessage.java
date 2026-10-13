package com.modeltech.datamasteryhub.modules.notification.channel;

import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;

/**
 * Un message à un destinataire, indépendant du canal (e-mail aujourd'hui, WhatsApp demain).
 * Chaque canal prend ce dont il a besoin : l'e-mail lit {@code toEmail}, WhatsApp lira {@code toPhone}.
 *
 * @param type          nature du message (tracée dans le journal : LIVE_REMINDER, SESSION_MESSAGE…)
 * @param body          version texte, utilisable par tous les canaux
 * @param replyTo       adresse de réponse (ex. le formateur qui écrit), facultative
 * @param email         mise en page riche pour le canal e-mail (boutons, détails…), facultative : à défaut,
 *                      l'e-mail est composé à partir de {@code subject} et {@code body}
 */
public record OutboundMessage(
        String type,
        String toEmail,
        String toPhone,
        String recipientName,
        String subject,
        String body,
        String replyTo,
        EmailContent email) {

    public OutboundMessage(String type, String toEmail, String toPhone, String recipientName,
                           String subject, String body, String replyTo) {
        this(type, toEmail, toPhone, recipientName, subject, body, replyTo, null);
    }

    public static OutboundMessage of(String type, String toEmail, String recipientName, String subject, String body) {
        return new OutboundMessage(type, toEmail, null, recipientName, subject, body, null, null);
    }

    /** Même message avec une mise en page e-mail riche. */
    public OutboundMessage withEmail(EmailContent content) {
        return new OutboundMessage(type, toEmail, toPhone, recipientName, subject, body, replyTo, content);
    }
}
