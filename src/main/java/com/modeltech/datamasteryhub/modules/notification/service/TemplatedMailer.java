package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import com.modeltech.datamasteryhub.modules.notification.template.EmailRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Point d'entrée unique pour écrire un e-mail : prend un {@link EmailContent}, produit un message
 * {@code multipart/alternative} (HTML de la charte + texte brut) avec l'expéditeur nommé, puis le confie à
 * {@link ResilientMailSender} (reprises, journal, redirection de recette). Ne lève jamais d'exception.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplatedMailer {

    private final ResilientMailSender sender;
    private final EmailRenderer renderer;

    public record Attachment(String filename, byte[] content, String contentType) {}

    public boolean send(String to, String subject, EmailContent content, String type) {
        return send(to, subject, content, type, null, List.of());
    }

    public boolean send(String to, String subject, EmailContent content, String type, String replyTo, List<Attachment> attachments) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = sender.fromAddress();
            // Sans adresse d'expéditeur (messagerie non configurée) l'envoi sera tracé « ignoré » par le sender
            helper.setFrom(from == null || from.isBlank() ? "noreply@localhost" : from, sender.fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
            EmailRenderer.Rendered rendered = renderer.render(content);
            helper.setText(rendered.text(), rendered.html());
            for (Attachment a : attachments) {
                helper.addAttachment(a.filename(), new ByteArrayResource(a.content()), a.contentType());
            }
            return sender.send(message, type);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            log.error("E-mail « {} » pour {} non composé : {}", type, to, e.getMessage());
            return false;
        }
    }
}
