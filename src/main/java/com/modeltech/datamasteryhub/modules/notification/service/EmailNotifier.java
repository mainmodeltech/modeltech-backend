package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Envoie un email de notification pour chaque nouvelle inscription.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotifier {

    private final JavaMailSender mailSender;

    @Value("${app.notifications.email.to:}")
    private String recipientEmail;

    @Value("${spring.mail.properties.mail.from:noreply@model-technologie.com}")
    private String fromEmail;

    public void send(Registration registration) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.debug("Email destinataire non configure, notification ignoree");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String fullName = registration.getFirstName() + " " + registration.getLastName();
            String bootcamp = registration.getBootcampTitle() != null
                    ? registration.getBootcampTitle()
                    : "Non specifie";

            helper.setFrom(fromEmail);
            helper.setTo(recipientEmail);
            helper.setSubject("Nouvelle inscription — " + fullName + " — " + bootcamp);
            helper.setText(buildHtmlBody(registration, fullName, bootcamp), true);

            mailSender.send(mimeMessage);
            log.info("Email de notification envoye a {} pour l'inscription de {}", recipientEmail, fullName);
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi de l'email de notification : {}", e.getMessage(), e);
        }
    }

    private String buildHtmlBody(Registration reg, String fullName, String bootcamp) {
        String date = reg.getCreatedAt() != null
                ? reg.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy a HH:mm"))
                : "—";

        return String.format("""
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; color: #333;">
                  <div style="background: linear-gradient(135deg, #1e3a5f 0%%, #0ea5e9 100%%); padding: 24px; border-radius: 12px 12px 0 0;">
                    <h1 style="color: white; margin: 0; font-size: 20px;">📋 Nouvelle inscription</h1>
                    <p style="color: rgba(255,255,255,0.8); margin: 8px 0 0;">Model Technologie — Data Mastery Hub</p>
                  </div>
                  <div style="border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 12px 12px; padding: 24px;">
                    <table style="width: 100%%; border-collapse: collapse;">
                      <tr>
                        <td style="padding: 8px 0; font-weight: 600; color: #6b7280; width: 140px;">Nom complet</td>
                        <td style="padding: 8px 0;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding: 8px 0; font-weight: 600; color: #6b7280;">Email</td>
                        <td style="padding: 8px 0;"><a href="mailto:%s" style="color: #0ea5e9;">%s</a></td>
                      </tr>
                      <tr>
                        <td style="padding: 8px 0; font-weight: 600; color: #6b7280;">Telephone</td>
                        <td style="padding: 8px 0;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding: 8px 0; font-weight: 600; color: #6b7280;">Bootcamp</td>
                        <td style="padding: 8px 0; font-weight: 600; color: #1e3a5f;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding: 8px 0; font-weight: 600; color: #6b7280;">Entreprise</td>
                        <td style="padding: 8px 0;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding: 8px 0; font-weight: 600; color: #6b7280;">Poste</td>
                        <td style="padding: 8px 0;">%s</td>
                      </tr>
                      %s
                    </table>
                    <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;" />
                    <p style="font-size: 13px; color: #9ca3af; margin: 0;">
                      Inscription recue le %s — Statut : En attente
                    </p>
                  </div>
                </body>
                </html>
                """,
                escapeHtml(fullName),
                escapeHtml(reg.getEmail()),
                escapeHtml(reg.getEmail()),
                escapeHtml(reg.getPhone() != null ? reg.getPhone() : "—"),
                escapeHtml(bootcamp),
                escapeHtml(reg.getCompany() != null ? reg.getCompany() : "—"),
                escapeHtml(reg.getPosition() != null ? reg.getPosition() : "—"),
                reg.getMessage() != null && !reg.getMessage().isBlank()
                        ? String.format("""
                          <tr>
                            <td style="padding: 8px 0; font-weight: 600; color: #6b7280; vertical-align: top;">Message</td>
                            <td style="padding: 8px 0; background: #f9fafb; border-radius: 8px; padding: 12px;">%s</td>
                          </tr>
                          """, escapeHtml(reg.getMessage()))
                        : "",
                date
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
