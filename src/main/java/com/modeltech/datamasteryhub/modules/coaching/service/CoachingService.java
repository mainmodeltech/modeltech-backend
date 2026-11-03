package com.modeltech.datamasteryhub.modules.coaching.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.RequestForm;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.RequestReceipt;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.RequestView;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.ReviewUpdate;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.Summary;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.ValidateRequest;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingRequest;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingStatus;
import com.modeltech.datamasteryhub.modules.coaching.repository.CoachingRequestRepository;
import com.modeltech.datamasteryhub.modules.legal.LegalContext;
import com.modeltech.datamasteryhub.modules.legal.service.LegalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Circuit de traitement d'une demande de coaching : demande publique → prise en charge et analyse par le responsable pédagogique → formateur
 * confirmé → <b>validation</b> (offre et montant fixés par l'équipe) qui envoie le lien de paiement → paiement (voir {@link CoachingPaymentService}).
 * Les transitions se font sous verrou de ligne : deux personnes qui agissent en même temps ne peuvent pas se marcher dessus.
 */
@Service
public class CoachingService {

    private static final int MAX_REQUESTS_PER_DAY_PER_EMAIL = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CoachingRequestRepository requests;
    private final CoachingMailer mailer;
    private final AuditService audit;
    private final LegalService legal;
    private final TransactionTemplate tx;

    /** Durée de validité du lien de paiement après la validation. */
    @Value("${app.coaching.link-validity-days:30}")
    private int linkValidityDays;

    public CoachingService(CoachingRequestRepository requests, CoachingMailer mailer, AuditService audit, LegalService legal, PlatformTransactionManager txManager) {
        this.requests = requests;
        this.mailer = mailer;
        this.audit = audit;
        this.legal = legal;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── Demande publique ────────────────────────────────────────────

    public RequestReceipt submit(RequestForm form) {
        String email = form.email().trim().toLowerCase(Locale.ROOT);
        legal.requireAccepted(LegalContext.COACHING_REQUEST, form.acceptedDocuments());   // contrôle serveur des conditions en vigueur
        CoachingRequest saved = tx.execute(s -> {
            if (requests.countByEmailIgnoreCaseAndCreatedAtAfter(email, LocalDateTime.now().minusDays(1)) >= MAX_REQUESTS_PER_DAY_PER_EMAIL) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vous avez déjà envoyé plusieurs demandes : nous vous répondons très vite.");
            }
            CoachingRequest r = new CoachingRequest();
            r.setFirstName(form.firstName().trim());
            r.setLastName(blankToNull(form.lastName()));
            r.setEmail(email);
            r.setPhone(blankToNull(form.phone()));
            r.setCompany(blankToNull(form.company()));
            r.setTier(blankToNull(form.tier()));
            r.setSlot(blankToNull(form.slot()));
            r.setMessage(blankToNull(form.message()));
            r.setTermsAcceptedAt(LocalDateTime.now());
            CoachingRequest created = requests.save(r);
            legal.record(LegalContext.COACHING_REQUEST, "COACHING_REQUEST", created.getId().toString(), email, created.fullName(), form.acceptedDocuments());
            return created;
        });
        mailer.requestReceived(saved);
        mailer.staffNewRequest(saved);
        return new RequestReceipt("Demande envoyée. Notre responsable pédagogique l'étudie et revient vers vous très vite.", mailer.bookingUrl());
    }

    // ── Consultation ────────────────────────────────────────────────

    public Page<RequestView> list(CoachingStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        return tx.execute(s -> (status == null ? requests.findAllByOrderByCreatedAtDesc(pageable) : requests.findAllByStatusOrderByCreatedAtDesc(status, pageable))
                .map(this::view));
    }

    public RequestView get(UUID id) {
        return tx.execute(s -> view(find(id)));
    }

    public Summary summary() {
        Map<CoachingStatus, Long> counts = new EnumMap<>(CoachingStatus.class);
        for (CoachingStatus st : CoachingStatus.values()) counts.put(st, requests.countByStatus(st));
        return new Summary(counts);
    }

    // ── Traitement par l'équipe ─────────────────────────────────────

    /** Prise en charge : la personne devient responsable de la demande, qui passe « en analyse ». */
    public RequestView take(UUID id, String actor) {
        return tx.execute(s -> {
            CoachingRequest r = lock(id);
            requireOpen(r, "prise en charge");
            r.setAssignedTo(actor);
            r.setStatus(CoachingStatus.IN_REVIEW);
            return view(requests.save(r));
        });
    }

    /** Formateur envisagé et notes internes (jamais montrées au client). */
    public RequestView update(UUID id, ReviewUpdate u, String actor) {
        return tx.execute(s -> {
            CoachingRequest r = lock(id);
            requireOpen(r, "modification");
            r.setTrainerEmail(blankToNull(u.trainerEmail()));
            r.setTrainerName(blankToNull(u.trainerName()));
            r.setInternalNotes(blankToNull(u.internalNotes()));
            if (r.getAssignedTo() == null) r.setAssignedTo(actor);
            if (r.getStatus() == CoachingStatus.NEW) r.setStatus(CoachingStatus.IN_REVIEW);
            return view(requests.save(r));
        });
    }

    /** Validation : le formateur est confirmé, l'offre et le montant sont fixés, le lien de paiement personnel est créé et envoyé. */
    public RequestView validate(UUID id, ValidateRequest v, String actor) {
        CoachingRequest r = tx.execute(s -> {
            CoachingRequest locked = lock(id);
            requireOpen(locked, "validation");
            locked.setOfferLabel(v.offerLabel().trim());
            locked.setAmountXof(v.amountXof());
            locked.setTrainerName(v.trainerName().trim());
            locked.setTrainerEmail(blankToNull(v.trainerEmail()));
            if (v.internalNotes() != null && !v.internalNotes().isBlank()) locked.setInternalNotes(v.internalNotes().trim());
            if (locked.getAssignedTo() == null) locked.setAssignedTo(actor);
            locked.setValidatedBy(actor);
            locked.setValidatedAt(LocalDateTime.now());
            locked.setPayToken(newToken());
            locked.setTokenExpiresAt(LocalDateTime.now().plusDays(linkValidityDays));
            locked.setStatus(CoachingStatus.AWAITING_PAYMENT);
            audit.record(AuditAction.COACHING_VALIDATED, "CoachingRequest", locked.getId(),
                    "Coaching de " + locked.fullName() + " validé par " + actor + " : " + locked.getOfferLabel() + ", " + v.amountXof() + " XOF, formateur " + locked.getTrainerName());
            return requests.save(locked);
        });
        mailer.paymentLink(r, false);
        return tx.execute(s -> view(find(id)));
    }

    /** Renvoie le lien de paiement au client (message perdu, relance). */
    public RequestView resendLink(UUID id) {
        CoachingRequest r = find(id);
        if (r.getStatus() != CoachingStatus.AWAITING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette demande n'attend pas de paiement (statut : " + r.getStatus() + ").");
        }
        mailer.paymentLink(r, true);
        return view(r);
    }

    /** Refus d'une demande avant validation ; le client reçoit le motif. */
    public RequestView decline(UUID id, String reason, String actor) {
        CoachingRequest r = tx.execute(s -> {
            CoachingRequest locked = lock(id);
            requireOpen(locked, "refus");
            locked.setStatus(CoachingStatus.DECLINED);
            locked.setClosingReason(reason.trim());
            audit.record(AuditAction.COACHING_DECLINED, "CoachingRequest", locked.getId(),
                    "Demande de coaching de " + locked.fullName() + " refusée par " + actor + " : " + reason.trim());
            return requests.save(locked);
        });
        mailer.declined(r);
        return tx.execute(s -> view(find(id)));
    }

    /** Annulation d'un coaching validé mais pas encore payé (le lien devient inutilisable). */
    public RequestView cancel(UUID id, String reason, String actor) {
        return tx.execute(s -> {
            CoachingRequest r = lock(id);
            if (!r.getStatus().acceptsPayment()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seul un coaching en attente de paiement peut être annulé (statut : " + r.getStatus() + ").");
            }
            r.setStatus(CoachingStatus.CANCELLED);
            r.setClosingReason(reason.trim());
            audit.record(AuditAction.COACHING_CANCELLED, "CoachingRequest", r.getId(),
                    "Coaching de " + r.fullName() + " annulé par " + actor + " : " + reason.trim());
            return view(requests.save(r));
        });
    }

    // ── Liens expirés ───────────────────────────────────────────────

    /**
     * Un lien non payé à temps expire. Une demande dont le client a déjà déclaré un paiement n'expire jamais : un humain doit trancher.
     * (Un paiement en ligne encaissé après coup est signalé par {@link CoachingPaymentService}, pas perdu.)
     */
    @Scheduled(fixedDelayString = "${app.coaching.expiry-sweep-ms:3600000}", initialDelayString = "${app.coaching.expiry-sweep-ms:3600000}")
    public void expireOldLinks() {
        List<CoachingRequest> late = requests.findByStatusInAndTokenExpiresAtBefore(List.of(CoachingStatus.AWAITING_PAYMENT), LocalDateTime.now());
        for (CoachingRequest r : late) {
            tx.executeWithoutResult(s -> requests.lockById(r.getId()).ifPresent(l -> {
                if (l.getStatus() == CoachingStatus.AWAITING_PAYMENT) {
                    l.setStatus(CoachingStatus.EXPIRED);
                    l.setClosingReason("Lien de paiement expiré");
                    requests.save(l);
                }
            }));
        }
    }

    // ── Aides ───────────────────────────────────────────────────────

    RequestView view(CoachingRequest r) {
        boolean linkActive = r.getPayToken() != null && r.getStatus().acceptsPayment();
        return new RequestView(r.getId(), r.getFirstName(), r.getLastName(), r.getEmail(), r.getPhone(), r.getCompany(), r.getTier(), r.getSlot(),
                r.getMessage(), r.getTermsAcceptedAt(), r.getStatus(), r.getAssignedTo(), r.getTrainerEmail(), r.getTrainerName(),
                r.getInternalNotes(), r.getClosingReason(), r.getOfferLabel(), r.getAmountXof(), r.getValidatedBy(), r.getValidatedAt(),
                linkActive ? mailer.paymentLink(r) : null, r.getTokenExpiresAt(), r.getManualMethod(), r.getManualReference(), r.getDeclaredAt(),
                r.getProofObjectKey() != null, r.getRejectNote(), r.getConfirmedBy(), r.getConfirmedAt(), r.getPaidAt(), r.getPaidAmountXof(),
                r.getPaidChannel(), r.getCreatedAt());
    }

    private CoachingRequest find(UUID id) {
        return requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("Demande de coaching", "id", id));
    }

    private CoachingRequest lock(UUID id) {
        return requests.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Demande de coaching", "id", id));
    }

    private static void requireOpen(CoachingRequest r, String what) {
        if (!r.getStatus().isOpenForReview()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette demande n'est plus à traiter : " + what + " impossible (statut : " + r.getStatus() + ").");
        }
    }

    /** 256 bits d'aléa : le lien de paiement n'est pas devinable. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
