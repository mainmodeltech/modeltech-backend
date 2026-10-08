package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.notification.entity.EmailLog;
import com.modeltech.datamasteryhub.modules.notification.repository.EmailLogRepository;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Point de sortie unique des e-mails : nouvelles tentatives sur les erreurs transitoires, trace de
 * chaque envoi (voir {@link EmailLog}, métadonnées seulement) et redirection optionnelle de tous les
 * destinataires vers une adresse de test ({@code app.mail.redirect-to}) pour les environnements hors production.
 *
 * <p>Ne lève jamais d'exception : un e-mail qui échoue ne doit pas faire échouer l'opération métier ;
 * l'échec est journalisé et consultable dans le back-office.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResilientMailSender {

    private final JavaMailSender delegate;
    private final EmailLogRepository logRepository;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:0}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.redirect-to:}")
    private String redirectTo;

    @Value("${app.mail.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.mail.retry-delay-ms:2000}")
    private long retryDelayMs;

    public MimeMessage createMimeMessage() {
        return delegate.createMimeMessage();
    }

    /** Expéditeur à utiliser : {@code app.mail.from}, sinon le compte SMTP (Gmail exige que ce soit le même). */
    public String fromAddress() {
        return from != null && !from.isBlank() ? from : username;
    }

    public boolean isConfigured() {
        return username != null && !username.isBlank();
    }

    public boolean send(MimeMessage message, String type) {
        String recipient = "?";
        String subject = null;
        try {
            recipient = firstRecipient(message);
            subject = message.getSubject();
            if (redirect()) {
                message.setRecipients(Message.RecipientType.TO, redirectTo);
                message.setSubject(redirectPrefix(recipient) + (subject == null ? "" : subject), "UTF-8");
            }
        } catch (MessagingException e) {
            return record(type, recipient, subject, EmailLog.FAILED, 0, "Message illisible : " + e.getMessage());
        }
        return deliver(type, recipient, subject, () -> delegate.send(message));
    }

    public boolean send(SimpleMailMessage message, String type) {
        String recipient = message.getTo() != null && message.getTo().length > 0 ? message.getTo()[0] : "?";
        String subject = message.getSubject();
        if (redirect()) {
            message.setTo(redirectTo);
            message.setSubject(redirectPrefix(recipient) + (subject == null ? "" : subject));
        }
        return deliver(type, recipient, subject, () -> delegate.send(message));
    }

    // =========================================================================
    //  ENVOI AVEC REPRISES
    // =========================================================================

    private boolean deliver(String type, String recipient, String subject, Runnable sending) {
        if (!isConfigured()) {
            log.warn("E-mail « {} » pour {} non envoyé : messagerie non configurée (MAIL_USERNAME vide)", type, recipient);
            return record(type, recipient, subject, EmailLog.SKIPPED, 0, "Messagerie non configurée");
        }
        int attempt = 0;
        String lastError = null;
        while (attempt < Math.max(1, maxAttempts)) {
            attempt++;
            try {
                sending.run();
                log.info("E-mail « {} » envoyé à {} (tentative {})", type, recipient, attempt);
                return record(type, recipient, subject, EmailLog.SENT, attempt, null);
            } catch (MailException e) {
                lastError = describe(e);
                boolean permanent = e instanceof MailAuthenticationException || e instanceof MailParseException;
                log.warn("E-mail « {} » vers {} : tentative {}/{} échouée — {}", type, recipient, attempt, maxAttempts, lastError);
                if (permanent) break;
                pause(retryDelayMs * attempt);
            }
        }
        log.error("E-mail « {} » vers {} abandonné après {} tentative(s) : {}", type, recipient, attempt, lastError);
        return record(type, recipient, subject, EmailLog.FAILED, attempt, lastError);
    }

    private void pause(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    //  OUTILS
    // =========================================================================

    private boolean redirect() {
        return redirectTo != null && !redirectTo.isBlank();
    }

    private String redirectPrefix(String originalRecipient) {
        return "[→ " + originalRecipient + "] ";
    }

    private String firstRecipient(MimeMessage message) throws MessagingException {
        Address[] all = message.getAllRecipients();
        return all != null && all.length > 0 ? all[0].toString() : "?";
    }

    /** Cause racine lisible, sans pile technique, bornée. */
    private String describe(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getClass().getSimpleName() + (root.getMessage() != null ? " : " + root.getMessage() : "");
        return message.length() > 900 ? message.substring(0, 900) : message;
    }

    private boolean record(String type, String recipient, String subject, String status, int attempts, String error) {
        try {
            EmailLog entry = new EmailLog();
            entry.setType(type);
            entry.setRecipient(redirect() ? recipient + " (redirigé)" : recipient);
            entry.setSubject(subject != null && subject.length() > 500 ? subject.substring(0, 500) : subject);
            entry.setStatus(status);
            entry.setAttempts(Math.max(1, attempts));
            entry.setError(error);
            if (EmailLog.SENT.equals(status)) entry.setSentAt(LocalDateTime.now());
            logRepository.save(entry);
        } catch (Exception e) {
            // Le journal est un confort : il ne doit jamais empêcher ni masquer l'envoi
            log.warn("Journal des e-mails indisponible : {}", e.getMessage());
        }
        return EmailLog.SENT.equals(status);
    }

    /** État de la messagerie, sans secret, pour le diagnostic. */
    public MailStatus status() {
        return new MailStatus(isConfigured(), host, port, fromAddress(), redirect() ? redirectTo : null, maxAttempts,
                logRepository.findFirstByStatusOrderByCreatedAtDesc(EmailLog.SENT).map(EmailLog::getSentAt).orElse(null),
                logRepository.findFirstByStatusOrderByCreatedAtDesc(EmailLog.FAILED).map(EmailLog::getCreatedAt).orElse(null),
                logRepository.findFirstByStatusOrderByCreatedAtDesc(EmailLog.FAILED).map(EmailLog::getError).orElse(null));
    }

    public record MailStatus(boolean configured, String host, int port, String from, String redirectTo,
                             int maxAttempts, LocalDateTime lastSentAt, LocalDateTime lastFailureAt, String lastError) {}
}
