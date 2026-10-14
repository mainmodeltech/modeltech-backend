package com.modeltech.datamasteryhub.modules.communication.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.dto.MasterclassPayloads;
import com.modeltech.datamasteryhub.modules.communication.entity.Masterclass;
import com.modeltech.datamasteryhub.modules.communication.entity.MasterclassRegistration;
import com.modeltech.datamasteryhub.modules.communication.repository.MasterclassRegistrationRepository;
import com.modeltech.datamasteryhub.modules.communication.repository.MasterclassRepository;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Événements « masterclass » : création et suivi par l'équipe, vue publique (sans lien de connexion), message aux inscrits.
 * Le lien de connexion n'est envoyé qu'aux inscrits (confirmation, rappels de l'équipe).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterclassEventService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH);

    private final MasterclassRepository repository;
    private final MasterclassRegistrationRepository registrations;
    private final MessageDispatcher dispatcher;

    @Value("${app.brand.name:Model Technologie}")
    private String brandName;

    // ── Équipe ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MasterclassPayloads.AdminEvent> list() {
        return repository.findAllByIsDeletedFalseOrderByStartsAtDesc().stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public MasterclassPayloads.AdminEvent get(UUID id) {
        return toAdmin(require(id));
    }

    @Transactional
    public MasterclassPayloads.AdminEvent create(MasterclassPayloads.EventRequest request) {
        Masterclass m = new Masterclass();
        m.setCode(uniqueCode(request.getTitle(), request.getStartsAt()));
        apply(m, request);
        return toAdmin(repository.save(m));
    }

    @Transactional
    public MasterclassPayloads.AdminEvent update(UUID id, MasterclassPayloads.EventRequest request) {
        Masterclass m = require(id);
        long registered = registrations.countByMasterclassIdAndIsDeletedFalse(m.getCode());
        if (request.getCapacity() != null && request.getCapacity() < registered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il y a déjà " + registered + " inscrits : la capacité ne peut pas être inférieure.");
        }
        apply(m, request);
        return toAdmin(repository.save(m));
    }

    /** Supprime un événement sans inscrit ; sinon refuse (on le dépublie ou on ferme les inscriptions). */
    @Transactional
    public void delete(UUID id) {
        Masterclass m = require(id);
        if (registrations.countByMasterclassIdAndIsDeletedFalse(m.getCode()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette masterclass a des inscrits : dépubliez-la ou fermez les inscriptions plutôt que de la supprimer.");
        }
        m.setDeleted(true);
        m.setDeletedAt(LocalDateTime.now());
        m.setPublished(false);
        repository.save(m);
    }

    /** Écrit à tous les inscrits (ou aux présents / absents) : rappel, lien, remerciements, support. */
    @Transactional(readOnly = true)
    public MasterclassPayloads.MessageResult message(UUID id, MasterclassPayloads.MessageRequest request, String actorEmail) {
        Masterclass m = require(id);
        String audience = request.getAudience() == null ? "ALL" : request.getAudience();
        List<MasterclassRegistration> targets = registrations.findAllByMasterclassIdAndIsDeletedFalseOrderByCreatedAtAsc(m.getCode()).stream()
                .filter(r -> "ALL".equals(audience) || audience.equals(r.getAttendance())).toList();
        String replyTo = actorEmail != null && actorEmail.contains("@") ? actorEmail : null;
        List<OutboundMessage> messages = targets.stream().map(r -> new OutboundMessage(
                "MASTERCLASS_MESSAGE", r.getEmail(), r.getPhone(), (r.getFirstName() + " " + r.getLastName()).trim(),
                request.getSubject(),
                "Bonjour " + r.getFirstName() + ",\n\n" + request.getBody() + "\n\n— " + brandName, replyTo,
                EmailContent.builder(request.getSubject()).greetingFor(r.getFirstName()).paragraph(request.getBody())
                        .details(detailsOf(m, true)).build())).toList();
        if (!messages.isEmpty()) dispatcher.sendAll(messages, null);
        log.info("Message masterclass « {} » envoyé à {} inscrit(s) ({}) par {}", m.getTitle(), messages.size(), audience, actorEmail);
        return MasterclassPayloads.MessageResult.builder().recipients(messages.size()).build();
    }

    // ── Public ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MasterclassPayloads.PublicEvent> upcoming(int limit) {
        return repository.upcoming(LocalDateTime.now(), Math.max(1, Math.min(limit, 12))).stream().map(this::toPublic).toList();
    }

    @Transactional(readOnly = true)
    public MasterclassPayloads.PublicEvent publicByCode(String code) {
        return repository.findByCodeAndIsDeletedFalse(code).filter(Masterclass::isPublished).map(this::toPublic)
                .orElseThrow(() -> new ResourceNotFoundException("Masterclass introuvable."));
    }

    /** L'événement ouvert aux inscriptions pour ce code, sinon refus lisible (utilisé à l'inscription). */
    @Transactional(readOnly = true)
    public Masterclass requireOpenForRegistration(String code) {
        Masterclass m = repository.findByCodeAndIsDeletedFalse(code).filter(Masterclass::isPublished)
                .orElseThrow(() -> new IllegalStateException("Cette masterclass n'existe pas ou n'est plus ouverte aux inscriptions."));
        if (m.isOver(LocalDateTime.now())) throw new IllegalStateException("Cette masterclass est terminée.");
        if (m.isRegistrationsClosed()) throw new IllegalStateException("Les inscriptions à cette masterclass sont closes.");
        if (m.getCapacity() != null && registrations.countByMasterclassIdAndIsDeletedFalse(m.getCode()) >= m.getCapacity()) {
            throw new IllegalStateException("Cette masterclass est complète.");
        }
        return m;
    }

    // ── Détails d'e-mail partagés avec la confirmation d'inscription ────

    public List<EmailContent.Entry> detailsOf(Masterclass m, boolean withLink) {
        java.util.ArrayList<EmailContent.Entry> entries = new java.util.ArrayList<>();
        entries.add(new EmailContent.Entry("Masterclass", m.getTitle()));
        entries.add(new EmailContent.Entry("Date", capitalize(DAY.format(m.getStartsAt())) + " à " + HOUR.format(m.getStartsAt())));
        entries.add(new EmailContent.Entry("Durée", m.getDurationMinutes() / 60 + " h" + (m.getDurationMinutes() % 60 > 0 ? " " + m.getDurationMinutes() % 60 : "")));
        if (m.getPlace() != null && !m.getPlace().isBlank()) entries.add(new EmailContent.Entry("Lieu", m.getPlace()));
        if (withLink && m.getJoinUrl() != null && !m.getJoinUrl().isBlank()) entries.add(new EmailContent.Entry("Lien de connexion", m.getJoinUrl()));
        return entries;
    }

    // ── Privé ────────────────────────────────────────────────────────────

    private void apply(Masterclass m, MasterclassPayloads.EventRequest r) {
        m.setTitle(r.getTitle().trim());
        m.setDescription(blankToNull(r.getDescription()));
        m.setStartsAt(r.getStartsAt());
        m.setDurationMinutes(r.getDurationMinutes());
        m.setFormat(r.getFormat() == null ? Masterclass.ONLINE : r.getFormat());
        m.setPlace(blankToNull(r.getPlace()));
        m.setJoinUrl(blankToNull(r.getJoinUrl()));
        m.setCapacity(r.getCapacity());
        m.setPublished(r.isPublished());
        m.setRegistrationsClosed(r.isRegistrationsClosed());
    }

    private Masterclass require(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Masterclass", "id", id));
    }

    private String uniqueCode(String title, LocalDateTime startsAt) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.length() > 50) base = base.substring(0, 50).replaceAll("-+$", "");
        if (base.isEmpty()) base = "masterclass";
        String stem = base + "-" + startsAt.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String code = stem;
        for (int i = 2; repository.existsByCode(code); i++) code = stem + "-" + i;
        return code;
    }

    private MasterclassPayloads.PublicEvent toPublic(Masterclass m) {
        long registered = registrations.countByMasterclassIdAndIsDeletedFalse(m.getCode());
        Integer left = m.getCapacity() == null ? null : (int) Math.max(0, m.getCapacity() - registered);
        boolean full = left != null && left == 0;
        boolean open = !m.isRegistrationsClosed() && !full && !m.isOver(LocalDateTime.now());
        return MasterclassPayloads.PublicEvent.builder().code(m.getCode()).title(m.getTitle()).description(m.getDescription())
                .startsAt(m.getStartsAt()).durationMinutes(m.getDurationMinutes()).format(m.getFormat()).place(m.getPlace())
                .spotsLeft(left).full(full).registrationOpen(open).build();
    }

    private MasterclassPayloads.AdminEvent toAdmin(Masterclass m) {
        long registered = registrations.countByMasterclassIdAndIsDeletedFalse(m.getCode());
        long attended = registrations.countByMasterclassIdAndAttendanceAndIsDeletedFalse(m.getCode(), "ATTENDED");
        LocalDateTime now = LocalDateTime.now();
        boolean full = m.getCapacity() != null && registered >= m.getCapacity();
        String status = !m.isPublished() ? "DRAFT" : m.isOver(now) ? "DONE"
                : (m.isRegistrationsClosed() || full) ? "CLOSED" : m.getStartsAt().isAfter(now) ? "OPEN" : "CLOSED";
        return MasterclassPayloads.AdminEvent.builder().id(m.getId()).code(m.getCode()).title(m.getTitle()).description(m.getDescription())
                .startsAt(m.getStartsAt()).durationMinutes(m.getDurationMinutes()).format(m.getFormat()).place(m.getPlace())
                .joinUrl(m.getJoinUrl()).capacity(m.getCapacity()).published(m.isPublished()).registrationsClosed(m.isRegistrationsClosed())
                .registered(registered).attended(attended).status(status).build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
