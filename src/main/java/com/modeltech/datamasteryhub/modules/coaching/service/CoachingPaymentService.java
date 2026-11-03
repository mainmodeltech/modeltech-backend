package com.modeltech.datamasteryhub.modules.coaching.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.CheckoutView;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.PayOptions;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.PayView;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.RequestView;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.StartResponse;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingCheckout;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingRequest;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingStatus;
import com.modeltech.datamasteryhub.modules.coaching.repository.CoachingCheckoutRepository;
import com.modeltech.datamasteryhub.modules.coaching.repository.CoachingRequestRepository;
import com.modeltech.datamasteryhub.modules.legal.LegalContext;
import com.modeltech.datamasteryhub.modules.legal.service.LegalService;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.payments.GatewayHealth;
import com.modeltech.datamasteryhub.modules.payments.OnlineCheckoutVerifier;
import com.modeltech.datamasteryhub.modules.payments.OnlineCheckoutVerifier.Verdict;
import com.modeltech.datamasteryhub.modules.payments.PayableService;
import com.modeltech.datamasteryhub.modules.payments.PaymentFees;
import com.modeltech.datamasteryhub.modules.payments.PaymentOptions;
import com.modeltech.datamasteryhub.modules.payments.PaymentPolicy;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutRequest;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.CheckoutSession;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayException;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.DeclareRequest;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderChannel;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Paiement d'un coaching validé, depuis le lien personnel envoyé au client : <b>en ligne</b> (passerelle) ou <b>manuel</b> (Wave, Orange Money,
 * virement déclarés avec justificatif, confirmés par la Finance). Quels moyens sont proposés se décide dans {@link PaymentPolicy} (réglages de la
 * Direction) ; le manuel sert toujours de secours.
 *
 * <p>Même règle d'or que pour le simulateur et les inscriptions : un paiement en ligne n'est jamais cru sur la foi du navigateur ni d'une
 * notification, il est relu chez le prestataire ({@link OnlineCheckoutVerifier}) et comparé au montant figé à la création de la tentative. Si de
 * l'argent est encaissé alors que la demande n'attend plus de paiement (annulée, expirée, déjà réglée à la main), rien n'est compté : alerte d'audit
 * pour un contrôle humain.
 */
@Service
@Slf4j
public class CoachingPaymentService {

    private static final int MAX_ATTEMPTS_PER_HOUR = 5;
    private static final Duration REUSE_WINDOW = Duration.ofMinutes(30);
    private static final Duration RECHECK_DELAY = Duration.ofSeconds(3);
    private static final Duration ABANDON_AFTER = Duration.ofHours(48);
    private static final int PROOF_LINK_MINUTES = 10;
    private static final String SUBJECT = "COACHING_PAYMENT";

    private final CoachingRequestRepository requests;
    private final CoachingCheckoutRepository checkouts;
    private final PaymentGateway gateway;
    private final PaymentPolicy policy;
    private final PaymentFees fees;
    private final GatewayHealth health;
    private final OnlineCheckoutVerifier verifier;
    private final StorageService storage;
    private final AuditService audit;
    private final CoachingMailer mailer;
    private final CoachingService coaching;
    private final LegalService legal;
    private final TransactionTemplate tx;

    @Value("${app.frontend.url:http://localhost:8088}")
    private String frontendUrl;

    public CoachingPaymentService(CoachingRequestRepository requests, CoachingCheckoutRepository checkouts, PaymentGateway gateway, PaymentPolicy policy,
                                  PaymentFees fees, GatewayHealth health, OnlineCheckoutVerifier verifier, StorageService storage, AuditService audit,
                                  CoachingMailer mailer, CoachingService coaching, LegalService legal, PlatformTransactionManager txManager) {
        this.requests = requests;
        this.checkouts = checkouts;
        this.gateway = gateway;
        this.policy = policy;
        this.fees = fees;
        this.health = health;
        this.verifier = verifier;
        this.storage = storage;
        this.audit = audit;
        this.mailer = mailer;
        this.coaching = coaching;
        this.legal = legal;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── Page du lien de paiement ────────────────────────────────────

    public PayView view(String token) {
        return tx.execute(s -> payView(byToken(token)));
    }

    private PayView payView(CoachingRequest r) {
        PaymentOptions o = policy.optionsFor(PayableService.COACHING, false);
        long amount = r.getAmountXof() == null ? 0 : r.getAmountXof();
        long total = fees.onlineTotal(amount);
        // Le motif d'un refus de paiement n'est montré que tant que le client doit corriger sa déclaration
        String note = r.getStatus() == CoachingStatus.AWAITING_PAYMENT ? r.getRejectNote() : null;
        return new PayView(r.getFirstName(), r.getOfferLabel(), r.getTrainerName(), r.getStatus(), amount,
                new PayOptions(o.online(), o.manual(), o.fallback(), o.reason(), total - amount, total),
                r.getManualMethod(), r.getProofObjectKey() != null, note, r.getPaidChannel(),
                r.getStatus() == CoachingStatus.PAID ? r.getReceiptUrl() : null, mailer.bookingUrl(), r.getTokenExpiresAt(),
                r.getStatus() == CoachingStatus.AWAITING_PAYMENT ? legal.pendingForSubject(LegalContext.COACHING_PAYMENT, SUBJECT, r.getId().toString()) : List.of());
    }

    // ── Conditions de vente : à accepter avant de payer ─────────────

    /** Le client accepte les conditions de vente en vigueur ; la preuve est enregistrée. Rien d'exigé : sans effet. */
    public PayView acceptTerms(String token, List<UUID> documentIds) {
        tx.executeWithoutResult(s -> {
            CoachingRequest r = byToken(token);
            if (r.getStatus() != CoachingStatus.AWAITING_PAYMENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce coaching n'attend plus de paiement.");
            }
            legal.requireAccepted(LegalContext.COACHING_PAYMENT, documentIds);
            legal.record(LegalContext.COACHING_PAYMENT, SUBJECT, r.getId().toString(), r.getEmail(), r.fullName(), documentIds);
        });
        return view(token);
    }

    private void requireTerms(CoachingRequest r) {
        if (!legal.hasAccepted(LegalContext.COACHING_PAYMENT, SUBJECT, r.getId().toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Acceptez d'abord les conditions générales de vente pour payer.");
        }
    }

    // ── Paiement en ligne : démarrage ───────────────────────────────

    private record Prepared(UUID id, UUID ref, String itemName, String description, long chargedXof, String name, String email, String phone, String link) {}

    public StartResponse startOnline(String token) {
        Object[] result = new Object[2];   // [StartResponse déjà prête | Prepared à envoyer]
        tx.executeWithoutResult(s -> {
            CoachingRequest r = byToken(token);
            if (r.getStatus() == CoachingStatus.PAYMENT_DECLARED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Votre paiement a déjà été déclaré : nous le vérifions.");
            }
            if (r.getStatus() != CoachingStatus.AWAITING_PAYMENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce coaching n'attend plus de paiement.");
            }
            requireTerms(r);
            PaymentOptions options = policy.optionsFor(PayableService.COACHING, false);
            if (!options.online()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, options.reason() != null ? options.reason() : "Le paiement en ligne n'est pas proposé pour ce coaching.");
            }
            // La même demande dans la demi-heure reprend la tentative ouverte (évite un double paiement)
            for (CoachingCheckout open : checkouts.findByRequestIdAndStatusOrderByCreatedAtDesc(r.getId(), CheckoutStatus.PENDING)) {
                if (open.getCheckoutUrl() != null && open.getCreatedAt().isAfter(LocalDateTime.now().minus(REUSE_WINDOW))) {
                    result[0] = new StartResponse(open.getPublicRef(), open.getCheckoutUrl());
                    return;
                }
            }
            if (checkouts.countByRequestIdAndCreatedAtAfter(r.getId(), LocalDateTime.now().minusHours(1)) >= MAX_ATTEMPTS_PER_HOUR) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives de paiement : patientez avant de réessayer.");
            }
            long base = r.getAmountXof();
            long charged = fees.onlineTotal(base);
            CoachingCheckout c = new CoachingCheckout();
            c.setRequest(r);
            c.setAmountXof(base);
            c.setFeeXof(charged - base);
            c.setChargedXof(charged);
            c.setProvider(gateway.provider());
            c.setProviderMode(gateway.mode());
            checkouts.save(c);
            long fee = charged - base;
            String back = frontendUrl + "/coaching/paiement/" + token + "?checkout=" + c.getPublicRef();
            result[1] = new Prepared(c.getId(), c.getPublicRef(), "Model Technologie — Coaching",
                    r.getOfferLabel() + (fee > 0 ? " (dont " + fee + " FCFA de frais de paiement en ligne)" : ""),
                    charged, r.fullName(), r.getEmail(), r.getPhone(), back);
        });
        if (result[0] != null) return (StartResponse) result[0];

        Prepared p = (Prepared) result[1];
        CheckoutSession session;
        try {
            session = gateway.createCheckout(new CheckoutRequest(p.ref().toString(), p.itemName(), p.description(), p.chargedXof(), p.name(),
                    p.email(), p.phone() == null || p.phone().isBlank() ? null : p.phone().trim(), p.link(), p.link() + "&annule=1"));
        } catch (GatewayException e) {
            health.recordFailure();
            tx.executeWithoutResult(s -> close(checkouts.findById(p.id()).orElseThrow(), CheckoutStatus.FAILED, "Création du paiement impossible : " + e.getMessage()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Le paiement en ligne est momentanément indisponible : vous pouvez payer par Wave, Orange Money ou virement.");
        }
        health.recordSuccess();
        tx.executeWithoutResult(s -> {
            CoachingCheckout c = checkouts.findById(p.id()).orElseThrow();
            c.setProviderToken(session.token());
            c.setCheckoutUrl(session.url());
            checkouts.save(c);
        });
        return new StartResponse(p.ref(), session.url());
    }

    // ── Paiement en ligne : vérification ────────────────────────────

    /** Notification du prestataire : simple déclencheur, l'état est relu chez lui. Retourne faux si le jeton n'est pas celui d'un coaching. */
    public boolean onNotification(String providerToken) {
        if (providerToken == null || providerToken.isBlank()) return false;
        var found = checkouts.findByProviderToken(providerToken.trim());
        found.ifPresent(c -> settle(c.getId()));
        return found.isPresent();
    }

    public CoachingCheckout settle(UUID id) {
        UUID[] paidRequest = new UUID[1];   // identifiant de la demande si ce contrôle vient de la marquer payée (e-mails hors transaction)
        CoachingCheckout c = tx.execute(s -> settleLocked(id, paidRequest));
        if (paidRequest[0] != null) mailer.paid(requests.findById(paidRequest[0]).orElseThrow());
        return c;
    }

    private CoachingCheckout settleLocked(UUID id, UUID[] paidRequest) {
        CoachingCheckout c = checkouts.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Paiement en ligne", "id", id));
        if (c.getStatus().isFinal() || c.getProviderToken() == null) return c;
        c.setLastCheckedAt(LocalDateTime.now());
        Verdict v = verifier.verify(c.getProviderToken(), c.getChargedXof(), c.getPublicRef().toString());
        switch (v.outcome()) {
            case PAID -> {
                CoachingRequest r = requests.lockById(c.getRequest().getId()).orElseThrow();
                if (!r.getStatus().acceptsPayment()) {
                    // L'argent est encaissé mais le coaching n'attend plus de paiement (annulé, expiré, déjà réglé) : un humain tranche
                    log.error("Paiement en ligne {} encaissé ({} XOF) mais le coaching est « {} » : à contrôler manuellement.", c.getPublicRef(), c.getChargedXof(), r.getStatus());
                    flag(c, "Paiement encaissé alors que le coaching est « " + r.getStatus() + " » : à contrôler manuellement.");
                } else {
                    markPaid(r, OrderChannel.ONLINE, c.getChargedXof(), "paiement-en-ligne", v.payment().receiptUrl());
                    c.setReceiptUrl(v.payment().receiptUrl());
                    c.setStatus(CheckoutStatus.PAID);
                    c.setPaidAt(LocalDateTime.now());
                    paidRequest[0] = r.getId();
                }
            }
            case MISMATCH -> {
                log.error("Paiement en ligne {} confirmé par le prestataire mais incohérent ({}) : à contrôler manuellement.", c.getPublicRef(), v.detail());
                flag(c, "Vérification impossible (" + v.detail() + ") : à contrôler manuellement.");
            }
            case CANCELLED -> close(c, CheckoutStatus.CANCELLED, v.detail());
            case FAILED -> close(c, CheckoutStatus.FAILED, v.detail());
            default -> { /* en attente, ou injoignable : on réessaiera (rapprochement) */ }
        }
        return checkouts.save(c);
    }

    private void markPaid(CoachingRequest r, OrderChannel channel, long amount, String confirmedBy, String receiptUrl) {
        LocalDateTime now = LocalDateTime.now();
        r.setStatus(CoachingStatus.PAID);
        r.setPaidAt(now);
        r.setPaidChannel(channel);
        r.setPaidAmountXof(amount);
        r.setConfirmedBy(confirmedBy);
        r.setConfirmedAt(now);
        r.setReceiptUrl(receiptUrl);
        r.setRejectNote(null);
        requests.save(r);
        audit.record(AuditAction.COACHING_PAID, "CoachingRequest", r.getId(),
                "Coaching de " + r.fullName() + " payé (" + amount + " XOF, " + (channel == OrderChannel.ONLINE ? "en ligne" : "manuel, confirmé par " + confirmedBy) + ")");
    }

    private void flag(CoachingCheckout c, String reason) {
        close(c, CheckoutStatus.FAILED, reason);
        audit.record(AuditAction.PAYMENT_ONLINE_MISMATCH, "CoachingCheckout", c.getId(),
                "Paiement en ligne de coaching de " + c.getChargedXof() + " XOF pour " + c.getRequest().fullName() + " : " + reason);
    }

    private void close(CoachingCheckout c, CheckoutStatus status, String reason) {
        if (c.getStatus().isFinal()) return;
        c.setStatus(status);
        c.setFailureReason(reason);
        checkouts.save(c);
    }

    /** Suivi public d'une tentative (référence opaque) ; relit le prestataire si elle est encore en attente. */
    public CheckoutView status(UUID ref) {
        CoachingCheckout c = checkouts.findByPublicRef(ref).orElseThrow(() -> new ResourceNotFoundException("Paiement en ligne", "ref", ref));
        if (c.getStatus() == CheckoutStatus.PENDING && c.getProviderToken() != null
                && (c.getLastCheckedAt() == null || c.getLastCheckedAt().isBefore(LocalDateTime.now().minus(RECHECK_DELAY)))) {
            settle(c.getId());
            c = checkouts.findByPublicRef(ref).orElseThrow();
        }
        return new CheckoutView(c.getPublicRef(), c.getStatus(), c.getChargedXof(), c.getStatus() == CheckoutStatus.PAID ? c.getReceiptUrl() : null);
    }

    /** Filet de sécurité : revérifie les tentatives restées en attente et abandonne celles jamais payées au bout de 48 h. */
    @Scheduled(fixedDelayString = "${app.payments.checkouts-sweep-ms:300000}", initialDelayString = "${app.payments.checkouts-sweep-ms:300000}")
    public void reconcile() {
        List<CoachingCheckout> pending = checkouts.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                CheckoutStatus.PENDING, LocalDateTime.now().minusMinutes(2), PageRequest.of(0, 100));
        for (CoachingCheckout c : pending) {
            try {
                CoachingCheckout after = settle(c.getId());
                if (after.getStatus() == CheckoutStatus.PENDING && after.getCreatedAt().isBefore(LocalDateTime.now().minus(ABANDON_AFTER))) {
                    tx.executeWithoutResult(s -> checkouts.lockById(c.getId()).ifPresent(l -> close(l, CheckoutStatus.EXPIRED, "Paiement non finalisé (tentative abandonnée)")));
                }
            } catch (RuntimeException e) {
                log.warn("Rapprochement du paiement de coaching {} impossible : {}", c.getPublicRef(), e.getMessage());
            }
        }
    }

    // ── Paiement manuel : déclaration du client, confirmation de la Finance ─────

    public PayView declare(String token, DeclareRequest req) {
        CoachingRequest r = tx.execute(s -> {
            CoachingRequest locked = requests.lockById(byToken(token).getId()).orElseThrow();
            if (locked.getStatus() == CoachingStatus.PAYMENT_DECLARED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Votre paiement a déjà été déclaré : nous le vérifions.");
            }
            if (locked.getStatus() != CoachingStatus.AWAITING_PAYMENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce coaching n'attend plus de paiement.");
            }
            requireTerms(locked);
            // Le manuel n'est qu'un secours quand le service est réglé « en ligne » et que la passerelle répond
            if (!policy.optionsFor(PayableService.COACHING, false).manual()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Le paiement manuel n'est pas proposé : réglez en ligne.");
            }
            locked.setManualMethod(req.method());
            locked.setManualReference(req.reference().trim());
            locked.setDeclaredAt(LocalDateTime.now());
            locked.setRejectNote(null);
            locked.setStatus(CoachingStatus.PAYMENT_DECLARED);
            audit.record(AuditAction.COACHING_PAYMENT_DECLARED, "CoachingRequest", locked.getId(),
                    "Paiement " + req.method() + " déclaré par " + locked.fullName() + " pour le coaching « " + locked.getOfferLabel() + " » ("
                            + locked.getAmountXof() + " XOF), référence « " + req.reference().trim() + " »");
            return requests.save(locked);
        });
        mailer.staffPaymentDeclared(r);
        return view(token);
    }

    /** Justificatif du paiement manuel : pièce privée, lien signé et de courte durée pour l'équipe. */
    public PayView uploadProof(String token, MultipartFile file) {
        CoachingRequest current = tx.execute(s -> byToken(token));
        if (current.getStatus() != CoachingStatus.AWAITING_PAYMENT && current.getStatus() != CoachingStatus.PAYMENT_DECLARED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce coaching n'accepte plus de justificatif.");
        }
        String previous = current.getProofObjectKey();
        String key = storage.uploadPrivateImage(file, "coaching-proofs").objectKey();
        tx.executeWithoutResult(s -> {
            CoachingRequest r = requests.findById(current.getId()).orElseThrow();
            r.setProofObjectKey(key);
            requests.save(r);
        });
        if (previous != null && !previous.isBlank()) {
            try {
                storage.delete(previous);
            } catch (RuntimeException e) {
                log.warn("Suppression de l'ancien justificatif impossible ({}): {}", previous, e.getMessage());
            }
        }
        return view(token);
    }

    /** Confirmation par la Finance après avoir vu l'argent. */
    public RequestView confirm(UUID id, String actor) {
        CoachingRequest r = tx.execute(s -> {
            CoachingRequest locked = requests.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Demande de coaching", "id", id));
            if (!locked.getStatus().acceptsPayment()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce paiement ne peut pas être confirmé (statut : " + locked.getStatus() + ").");
            }
            markPaid(locked, OrderChannel.MANUAL, locked.getAmountXof(), actor, null);
            return locked;
        });
        mailer.paid(r);
        return tx.execute(s -> coaching.view(requests.findById(id).orElseThrow()));
    }

    /** Refus d'un paiement déclaré (montant ou justificatif non conforme) : le client peut déclarer de nouveau. */
    public RequestView reject(UUID id, String reason, String actor) {
        return tx.execute(s -> {
            CoachingRequest r = requests.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Demande de coaching", "id", id));
            if (r.getStatus() != CoachingStatus.PAYMENT_DECLARED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seul un paiement déclaré peut être refusé (statut : " + r.getStatus() + ").");
            }
            r.setStatus(CoachingStatus.AWAITING_PAYMENT);
            r.setRejectNote(reason.trim());
            r.setManualMethod(null);
            r.setManualReference(null);
            r.setDeclaredAt(null);
            audit.record(AuditAction.COACHING_PAYMENT_REJECTED, "CoachingRequest", r.getId(),
                    "Paiement manuel de " + r.fullName() + " refusé par " + actor + " : " + reason.trim());
            return coaching.view(requests.save(r));
        });
    }

    /** Lien signé (10 minutes) vers le justificatif, pour l'équipe habilitée. */
    public String proofLink(UUID id) {
        CoachingRequest r = requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("Demande de coaching", "id", id));
        if (r.getProofObjectKey() == null || r.getProofObjectKey().isBlank()) throw new ResourceNotFoundException("Justificatif", "demande", id);
        return storage.presignedGetUrl(r.getProofObjectKey(), PROOF_LINK_MINUTES);
    }

    // ── Aides ───────────────────────────────────────────────────────

    private CoachingRequest byToken(String token) {
        CoachingRequest r = requests.findByPayToken(token).orElseThrow(() -> new ResourceNotFoundException("Lien de paiement invalide."));
        if (r.getStatus().acceptsPayment() && r.getTokenExpiresAt() != null && r.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Ce lien de paiement a expiré. Contactez Model Technologie pour en recevoir un nouveau.");
        }
        return r;
    }
}
