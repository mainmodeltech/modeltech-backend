package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;

/**
 * Envoie une notification Slack via Incoming Webhook.
 */
@Component
@Slf4j
public class SlackNotifier {

    @Value("${app.notifications.slack.webhook-url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void send(Registration registration) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Slack webhook URL non configuree, notification ignoree");
            return;
        }

        try {
            String bootcamp = registration.getBootcampTitle() != null
                    ? registration.getBootcampTitle()
                    : "Non specifie";

            String sessionInfo = registration.getSessionName() != null
                    ? registration.getSessionName()
                    : "—";

            String promoInfo = registration.getPromoCodeUsed() != null
                    ? registration.getPromoCodeUsed() + " (-" + registration.getDiscountPercent() + "%)"
                    : "—";

            String date = registration.getCreatedAt() != null
                    ? registration.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "—";

            String payload = String.format("""
                    {
                      "blocks": [
                        {
                          "type": "header",
                          "text": { "type": "plain_text", "text": "📋 Nouvelle inscription !", "emoji": true }
                        },
                        {
                          "type": "section",
                          "fields": [
                            { "type": "mrkdwn", "text": "*Nom :*\\n%s %s" },
                            { "type": "mrkdwn", "text": "*Email :*\\n%s" },
                            { "type": "mrkdwn", "text": "*Telephone :*\\n%s" },
                            { "type": "mrkdwn", "text": "*Bootcamp :*\\n%s" },
                            { "type": "mrkdwn", "text": "*Session :*\\n%s" },
                            { "type": "mrkdwn", "text": "*Code promo :*\\n%s" },
                            { "type": "mrkdwn", "text": "*Entreprise :*\\n%s" },
                            { "type": "mrkdwn", "text": "*Poste :*\\n%s" }
                          ]
                        },
                        %s
                        {
                          "type": "context",
                          "elements": [
                            { "type": "mrkdwn", "text": "📅 %s | Statut : En attente" }
                          ]
                        }
                      ]
                    }
                    """,
                    escapeJson(registration.getFirstName()),
                    escapeJson(registration.getLastName()),
                    escapeJson(registration.getEmail()),
                    escapeJson(registration.getPhone() != null ? registration.getPhone() : "—"),
                    escapeJson(bootcamp),
                    escapeJson(sessionInfo),
                    escapeJson(promoInfo),
                    escapeJson(registration.getCompany() != null ? registration.getCompany() : "—"),
                    escapeJson(registration.getPosition() != null ? registration.getPosition() : "—"),
                    registration.getMessage() != null && !registration.getMessage().isBlank()
                            ? String.format("""
                              {
                                "type": "section",
                                "text": { "type": "mrkdwn", "text": "*Message :*\\n> %s" }
                              },
                              """, escapeJson(registration.getMessage()))
                            : "",
                    date
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Notification Slack envoyee pour l'inscription de {} {}",
                        registration.getFirstName(), registration.getLastName());
            } else {
                log.warn("Slack a repondu avec le statut {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de la notification Slack : {}", e.getMessage(), e);
        }
    }

    // ── Message de contact ─────────────────────────────────────────────────

    public void sendContactMessage(ContactMessage contact) {
        if (isDisabled()) return;

        try {
            String date = contact.getCreatedAt() != null
                    ? contact.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";

            String sujetBlock = contact.getSubject() != null && !contact.getSubject().isBlank()
                    ? String.format(
                    "{ \"type\": \"mrkdwn\", \"text\": \"*Sujet :*\\n%s\" },",
                    escapeJson(contact.getSubject()))
                    : "";

            String payload = String.format("""
                    {
                      "blocks": [
                        {
                          "type": "header",
                          "text": { "type": "plain_text", "text": "✉️ Nouveau message de contact !", "emoji": true }
                        },
                        {
                          "type": "section",
                          "fields": [
                            { "type": "mrkdwn", "text": "*Nom :*\\n%s %s" },
                            { "type": "mrkdwn", "text": "*Email :*\\n%s" },
                            %s
                            { "type": "mrkdwn", "text": "*Entreprise :*\\n%s" }
                          ]
                        },
                        {
                          "type": "section",
                          "text": { "type": "mrkdwn", "text": "*Message :*\\n> %s" }
                        },
                        {
                          "type": "context",
                          "elements": [
                            { "type": "mrkdwn", "text": "📅 %s | Statut : Non lu" }
                          ]
                        }
                      ]
                    }
                    """,
                    escapeJson(contact.getFirstName()),
                    escapeJson(contact.getLastName()),
                    escapeJson(contact.getEmail()),
                    sujetBlock,
                    escapeJson(contact.getCompany() != null ? contact.getCompany() : "—"),
                    escapeJson(contact.getMessage()),
                    date
            );

            postToSlack(payload);
            log.info("Notification Slack (contact) envoyée pour {} {}",
                    contact.getFirstName(), contact.getLastName());

        } catch (Exception e) {
            log.error("Erreur Slack (contact) : {}", e.getMessage(), e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void postToSlack(String jsonPayload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Slack a répondu avec le statut {}", response.getStatusCode());
        }
    }

    private boolean isDisabled() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Slack webhook URL non configurée, notification ignorée");
            return true;
        }
        return false;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
