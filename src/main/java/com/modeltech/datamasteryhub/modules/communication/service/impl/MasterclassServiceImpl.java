package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;

import com.modeltech.datamasteryhub.modules.communication.dto.request.MasterclassRegistrationRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.MasterclassRegistrationResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.MasterclassRegistration;
import com.modeltech.datamasteryhub.modules.communication.repository.MasterclassRegistrationRepository;
import com.modeltech.datamasteryhub.modules.communication.service.MasterclassService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MasterclassServiceImpl implements MasterclassService  {

    private final MasterclassRegistrationRepository repository;
    private final JavaMailSender mailSender;

    @Value("${app.notifications.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${spring.mail.properties.mail.from:noreply@model-technologie.com}")
    private String fromEmail;

    @Value("${app.masterclass.meet-link:https://tel.meet/bqy-eyst-bgj?pin=5837873367976}")
    private String meetLink;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Inscription ────────────────────────────────────────────────────────

    @Transactional
    public MasterclassRegistrationResponseDTO register(MasterclassRegistrationRequestDTO req) {
        // Vérifier doublon
        if (repository.existsByMasterclassIdAndEmailAndIsDeletedFalse(
                req.getMasterclassId(), req.getEmail())) {
            throw new IllegalStateException("Cet email est déjà inscrit à cette masterclass.");
        }

        MasterclassRegistration reg = new MasterclassRegistration();
        reg.setMasterclassId(req.getMasterclassId());
        reg.setFirstName(req.getFirstName());
        reg.setLastName(req.getLastName());
        reg.setEmail(req.getEmail());
        reg.setPhone(req.getPhone());
        reg.setProfile(req.getProfile());
        reg.setCompany(req.getCompany());

        MasterclassRegistration saved = repository.save(reg);

        // Notifications asynchrones — ne bloquent pas la réponse
        sendConfirmationEmail(saved);
        notifySlack(saved);

        return toResponse(saved);
    }

    // ── Admin ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<MasterclassRegistrationResponseDTO> getAll(String masterclassId, int page, int size) {
        return repository
                .findByMasterclassIdAndIsDeletedFalse(
                        masterclassId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long count(String masterclassId) {
        return repository.countByMasterclassIdAndIsDeletedFalse(masterclassId);
    }

    // ── Email de confirmation ──────────────────────────────────────────────

    @Async
    protected void sendConfirmationEmail(MasterclassRegistration reg) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(reg.getEmail());
            helper.setSubject("✅ Inscription confirmée — Masterclass Power BI · 24 mars 2026");
            helper.setText(buildConfirmationHtml(reg), true);

            mailSender.send(msg);

            reg.setEmailSent(true);
            repository.save(reg);

            log.info("Email de confirmation envoyé à {}", reg.getEmail());
        } catch (MessagingException e) {
            log.error("Erreur envoi email masterclass : {}", e.getMessage(), e);
        }
    }

    // ── Notification Slack ─────────────────────────────────────────────────

    @Async
    protected void notifySlack(MasterclassRegistration reg) {
        if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) return;
        try {
            String payload = String.format("""
                {
                  "blocks": [
                    {
                      "type": "header",
                      "text": { "type": "plain_text", "text": "🎓 Nouvelle inscription Masterclass !", "emoji": true }
                    },
                    {
                      "type": "section",
                      "fields": [
                        { "type": "mrkdwn", "text": "*Nom :*\\n%s %s" },
                        { "type": "mrkdwn", "text": "*Email :*\\n%s" },
                        { "type": "mrkdwn", "text": "*Profil :*\\n%s" },
                        { "type": "mrkdwn", "text": "*Entreprise :*\\n%s" },
                        { "type": "mrkdwn", "text": "*Tél :*\\n%s" }
                      ]
                    },
                    {
                      "type": "context",
                      "elements": [
                        { "type": "mrkdwn", "text": "📅 Masterclass Power BI — Vendredi 20 mars 2026 · 18h-20h" }
                      ]
                    }
                  ]
                }
                """,
                    escapeJson(reg.getFirstName()),
                    escapeJson(reg.getLastName()),
                    escapeJson(reg.getEmail()),
                    escapeJson(reg.getProfile() != null ? reg.getProfile() : "—"),
                    escapeJson(reg.getCompany() != null ? reg.getCompany() : "—"),
                    escapeJson(reg.getPhone() != null ? reg.getPhone() : "—")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(slackWebhookUrl, new HttpEntity<>(payload, headers), String.class);

            reg.setSlackNotified(true);
            repository.save(reg);

        } catch (Exception e) {
            log.error("Erreur Slack masterclass : {}", e.getMessage(), e);
        }
    }

    // ── HTML email ─────────────────────────────────────────────────────────

    private String buildConfirmationHtml(MasterclassRegistration reg) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; color: #1a1a2e; background: #f8f9fa;">

              <!-- Header -->
              <div style="background: linear-gradient(135deg, #1a1a2e 0%%, #16213e 50%%, #0f3460 100%%); padding: 40px 32px; border-radius: 16px 16px 0 0; text-align: center;">
                <div style="display: inline-block; background: rgba(251,191,36,0.15); border: 1px solid rgba(251,191,36,0.4); border-radius: 999px; padding: 6px 16px; margin-bottom: 16px;">
                  <span style="color: #fbbf24; font-size: 12px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase;">GRATUIT · EN LIGNE</span>
                </div>
                <h1 style="color: #fff; margin: 0 0 8px; font-size: 22px; font-weight: 800;">🎉 Vous êtes inscrit(e) !</h1>
                <p style="color: rgba(255,255,255,0.7); margin: 0; font-size: 15px;">Masterclass Power BI — Model Technologie</p>
              </div>

              <!-- Body -->
              <div style="background: #fff; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 16px 16px; padding: 32px;">

                <p style="font-size: 16px; margin: 0 0 24px;">Bonjour <strong>%s</strong>,</p>
                <p style="color: #4b5563; margin: 0 0 24px; line-height: 1.7;">
                  Votre inscription à la masterclass <strong>"Construire son premier tableau de bord avec Microsoft Power BI"</strong> est bien confirmée. Rendez-vous le <strong>Mardi 24 mars 2026 de 18h à 20h</strong>.
                </p>

                <!-- Event card -->
                <div style="background: #f8f9fa; border: 2px solid #fbbf24; border-radius: 12px; padding: 24px; margin-bottom: 28px;">
                  <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
                    <div style="font-size: 28px;">📅</div>
                    <div>
                      <p style="margin: 0; font-weight: 800; font-size: 17px; color: #1a1a2e;">Mardi 24 mars 2026</p>
                      <p style="margin: 4px 0 0; color: #6b7280; font-size: 14px;">18h00 – 20h00 (heure de Dakar, GMT+0)</p>
                    </div>
                  </div>
                  <div style="border-top: 1px solid #e5e7eb; padding-top: 16px;">
                    <p style="margin: 0 0 12px; font-size: 13px; color: #6b7280; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">Lien de la session</p>
                    <a href="%s" style="display: inline-block; background: #1a1a2e; color: #fbbf24; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: 700; font-size: 15px;">
                      🎥 Rejoindre sur Google Meet
                    </a>
                  </div>
                </div>

                <!-- Ce que vous allez apprendre -->
                <h3 style="color: #1a1a2e; font-size: 15px; margin: 0 0 12px; font-weight: 700;">Ce que vous allez apprendre :</h3>
                <ul style="padding-left: 20px; color: #4b5563; line-height: 2; margin: 0 0 24px;">
                  <li>Comprendre l'interface Power BI Desktop</li>
                  <li>Importer et préparer vos données</li>
                  <li>Créer des visualisations percutantes</li>
                  <li>Construire votre premier dashboard complet</li>
                  <li>Partager et publier votre rapport</li>
                </ul>

                <!-- Note -->
                <div style="background: #fffbeb; border-left: 4px solid #fbbf24; padding: 14px 16px; border-radius: 0 8px 8px 0; margin-bottom: 28px;">
                  <p style="margin: 0; font-size: 14px; color: #92400e;">
                    💡 <strong>Préparez-vous :</strong> Téléchargez Power BI Desktop gratuitement sur <a href="https://powerbi.microsoft.com" style="color: #d97706;">powerbi.microsoft.com</a> avant la session.
                  </p>
                </div>

                <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;" />
                <p style="font-size: 13px; color: #9ca3af; margin: 0; text-align: center;">
                  Model Technologie · Dakar, Sénégal<br />
                  Des questions ? Répondez directement à cet email.
                </p>
              </div>
            </body>
            </html>
            """,
                escapeHtml(reg.getFirstName()),
                meetLink
        );
    }

    // ── Mapper ─────────────────────────────────────────────────────────────

    private MasterclassRegistrationResponseDTO toResponse(MasterclassRegistration reg) {
        MasterclassRegistrationResponseDTO r = new MasterclassRegistrationResponseDTO();
        r.setId(reg.getId());
        r.setMasterclassId(reg.getMasterclassId());
        r.setFirstName(reg.getFirstName());
        r.setLastName(reg.getLastName());
        r.setEmail(reg.getEmail());
        r.setPhone(reg.getPhone());
        r.setProfile(reg.getProfile());
        r.setCompany(reg.getCompany());
        r.setEmailSent(reg.getEmailSent());
        r.setCreatedAt(reg.getCreatedAt());
        return r;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
