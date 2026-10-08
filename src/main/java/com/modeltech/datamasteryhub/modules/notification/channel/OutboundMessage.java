package com.modeltech.datamasteryhub.modules.notification.channel;

/**
 * Un message à un destinataire, indépendant du canal (e-mail aujourd'hui, WhatsApp demain).
 * Chaque canal prend ce dont il a besoin : l'e-mail lit {@code toEmail}, WhatsApp lira {@code toPhone}.
 *
 * @param type          nature du message (tracée dans le journal : LIVE_REMINDER, SESSION_MESSAGE…)
 * @param replyTo       adresse de réponse (ex. le formateur qui écrit), facultative
 */
public record OutboundMessage(
        String type,
        String toEmail,
        String toPhone,
        String recipientName,
        String subject,
        String body,
        String replyTo) {

    public static OutboundMessage of(String type, String toEmail, String recipientName, String subject, String body) {
        return new OutboundMessage(type, toEmail, null, recipientName, subject, body, null);
    }
}
