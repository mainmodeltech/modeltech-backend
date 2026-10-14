package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.dto.request.MasterclassRegistrationRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.MasterclassRegistrationResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.Masterclass;
import com.modeltech.datamasteryhub.modules.communication.entity.MasterclassRegistration;
import com.modeltech.datamasteryhub.modules.communication.repository.MasterclassRegistrationRepository;
import com.modeltech.datamasteryhub.modules.communication.service.MasterclassEventService;
import com.modeltech.datamasteryhub.modules.communication.service.MasterclassService;
import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Inscriptions aux masterclass. Une inscription n'est acceptée que pour un événement publié, pas terminé, ouvert et non complet
 * (voir {@link MasterclassEventService#requireOpenForRegistration}). La confirmation (date, lieu, lien) part par la messagerie
 * de la plateforme, comme tous les autres e-mails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterclassServiceImpl implements MasterclassService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH);

    private final MasterclassRegistrationRepository repository;
    private final MasterclassEventService events;
    private final MessageDispatcher dispatcher;
    private final RecaptchaService recaptchaService;

    @Value("${app.notifications.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${app.brand.name:Model Technologie}")
    private String brandName;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Inscription ────────────────────────────────────────────────────────

    @Transactional
    @Override
    public MasterclassRegistrationResponseDTO register(MasterclassRegistrationRequestDTO req) {
        // 1. Vérification reCAPTCHA — avant tout traitement BDD
        if (!recaptchaService.verify(req.getRecaptchaToken(), "masterclass_register")) {
            log.error("Échec vérification reCAPTCHA pour email {} et masterclass {}", req.getEmail(), req.getMasterclassId());
            throw new SecurityException("Vérification anti-bot échouée. Veuillez réessayer.");
        }

        // 2. L'événement doit exister, être publié, ouvert et avoir de la place
        Masterclass event = events.requireOpenForRegistration(req.getMasterclassId());

        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByMasterclassIdAndEmailAndIsDeletedFalse(event.getCode(), email)) {
            throw new IllegalStateException("Cet email est déjà inscrit à cette masterclass.");
        }

        MasterclassRegistration reg = new MasterclassRegistration();
        reg.setMasterclassId(event.getCode());
        reg.setFirstName(req.getFirstName().trim());
        reg.setLastName(req.getLastName().trim());
        reg.setEmail(email);
        reg.setPhone(req.getPhone());
        reg.setProfile(req.getProfile());
        reg.setCompany(req.getCompany());

        MasterclassRegistration saved = repository.save(reg);
        log.info("Inscription masterclass enregistrée : id={}, email={}, masterclass={}", saved.getId(), saved.getEmail(), event.getCode());

        sendConfirmation(saved, event);
        notifySlack(saved, event);
        return toResponse(saved);
    }

    // ── Admin ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public Page<MasterclassRegistrationResponseDTO> getAll(String masterclassId, int page, int size) {
        return repository.findByMasterclassIdAndIsDeletedFalse(masterclassId,
                PageRequest.of(page, Math.min(Math.max(1, size), 500), Sort.by(Sort.Direction.DESC, "createdAt"))).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public long count(String masterclassId) {
        return repository.countByMasterclassIdAndIsDeletedFalse(masterclassId);
    }

    @Transactional
    @Override
    public MasterclassRegistrationResponseDTO setAttendance(String masterclassId, UUID registrationId, String attendance) {
        MasterclassRegistration reg = repository.findByIdAndIsDeletedFalse(registrationId)
                .filter(r -> r.getMasterclassId().equals(masterclassId))
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", registrationId));
        reg.setAttendance(attendance);
        return toResponse(repository.save(reg));
    }

    // ── E-mail de confirmation (messagerie de la plateforme) ───────────────

    private void sendConfirmation(MasterclassRegistration reg, Masterclass event) {
        String when = WHEN.format(event.getStartsAt());
        EmailContent content = EmailContent.builder("Vous êtes inscrit(e) !")
                .preheader(event.getTitle() + " — " + when)
                .greetingFor(reg.getFirstName())
                .paragraph("Votre inscription à la masterclass **" + event.getTitle() + "** est confirmée. Nous vous y attendons.")
                .details(events.detailsOf(event, true))
                .button(event.getJoinUrl() != null && !event.getJoinUrl().isBlank() ? "Rejoindre la masterclass" : null, event.getJoinUrl())
                .note("Un rappel vous sera envoyé avant l'événement. Besoin d'aide ? Répondez simplement à cet e-mail.")
                .build();
        String body = "Bonjour %s,\n\nVotre inscription à la masterclass « %s » est confirmée.\n%s\n%s\n\n— %s".formatted(
                reg.getFirstName(), event.getTitle(), when, event.getJoinUrl() == null ? "" : "Lien : " + event.getJoinUrl(), brandName);
        boolean sent = dispatcher.send(new OutboundMessage("MASTERCLASS_CONFIRMATION", reg.getEmail(), reg.getPhone(),
                (reg.getFirstName() + " " + reg.getLastName()).trim(), "Inscription confirmée — " + event.getTitle(), body, null, content));
        if (sent) {
            reg.setEmailSent(true);
            repository.save(reg);
        }
    }

    // ── Notification Slack ─────────────────────────────────────────────────

    @Async
    protected void notifySlack(MasterclassRegistration reg, Masterclass event) {
        if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) return;
        try {
            String payload = String.format("""
                {
                  "blocks": [
                    { "type": "header", "text": { "type": "plain_text", "text": "🎓 Nouvelle inscription Masterclass !", "emoji": true } },
                    { "type": "section", "fields": [
                        { "type": "mrkdwn", "text": "*Nom :*\\n%s %s" },
                        { "type": "mrkdwn", "text": "*Email :*\\n%s" },
                        { "type": "mrkdwn", "text": "*Profil :*\\n%s" },
                        { "type": "mrkdwn", "text": "*Entreprise :*\\n%s" },
                        { "type": "mrkdwn", "text": "*Tél :*\\n%s" } ] },
                    { "type": "context", "elements": [ { "type": "mrkdwn", "text": "📅 %s — %s" } ] }
                  ]
                }
                """,
                    escapeJson(reg.getFirstName()), escapeJson(reg.getLastName()), escapeJson(reg.getEmail()),
                    escapeJson(reg.getProfile() != null ? reg.getProfile() : "—"),
                    escapeJson(reg.getCompany() != null ? reg.getCompany() : "—"),
                    escapeJson(reg.getPhone() != null ? reg.getPhone() : "—"),
                    escapeJson(event.getTitle()), escapeJson(WHEN.format(event.getStartsAt())));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(slackWebhookUrl, new HttpEntity<>(payload, headers), String.class);
            reg.setSlackNotified(true);
            repository.save(reg);
        } catch (Exception e) {
            log.error("Erreur Slack masterclass : {}", e.getMessage(), e);
        }
    }

    // ── Mapping ────────────────────────────────────────────────────────────

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
        r.setAttendance(reg.getAttendance());
        r.setCreatedAt(reg.getCreatedAt());
        return r;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
