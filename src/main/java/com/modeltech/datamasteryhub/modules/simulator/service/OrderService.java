package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.service.LearnerService;
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
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.OrderStatusView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.OrderView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PurchaseRequest;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PurchaseResponse;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderChannel;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimOrder;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimPlan;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimOrderRepository;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimPlanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Commandes d'un forfait du simulateur, payées <b>en ligne</b> (passerelle) ou <b>manuellement</b> (Wave, Orange Money, virement déclarés
 * avec justificatif, confirmés par l'équipe). Quels moyens sont proposés se décide dans {@link PaymentPolicy} (réglages de la Direction) ;
 * le manuel sert toujours de secours.
 *
 * <p><b>Règle d'or : un accès ne s'ouvre jamais sur la foi du navigateur ni d'une notification.</b> En ligne, une commande ne devient
 * « payée » qu'après avoir interrogé le prestataire (jeton → statut, montant, référence), sous verrou de ligne : une notification reçue deux
 * fois, ou en même temps qu'un contrôle, ouvre un seul accès (et la base interdit un second accès par commande). En manuel, c'est une
 * personne de l'équipe qui confirme après avoir vu l'argent. Les appels au prestataire se font hors de toute transaction ouverte à la
 * création ; la vérification, elle, garde le verrou le temps de l'appel (quelques secondes au plus, volume faible).
 */
@Service
@Slf4j
public class OrderService {

    private static final int MAX_PENDING_PER_HOUR = 5;
    private static final Duration RECHECK_DELAY = Duration.ofSeconds(3);
    private static final Duration ABANDON_ONLINE_AFTER = Duration.ofHours(48);
    private static final Duration ABANDON_MANUAL_AFTER = Duration.ofDays(7);
    private static final int PROOF_LINK_MINUTES = 10;

    private final SimPlanRepository plans;
    private final SimOrderRepository orders;
    private final LearnerService learnerService;
    private final AccessService access;
    private final PaymentGateway gateway;
    private final PaymentPolicy policy;
    private final PaymentFees fees;
    private final GatewayHealth health;
    private final OnlineCheckoutVerifier verifier;
    private final StorageService storage;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    @Value("${app.frontend.url:http://localhost:8088}")
    private String frontendUrl;

    public OrderService(SimPlanRepository plans, SimOrderRepository orders, LearnerService learnerService, AccessService access,
                        PaymentGateway gateway, PaymentPolicy policy, PaymentFees fees, GatewayHealth health, OnlineCheckoutVerifier verifier, StorageService storage,
                        AuditService audit, ApplicationEventPublisher events, PlatformTransactionManager txManager) {
        this.plans = plans;
        this.orders = orders;
        this.learnerService = learnerService;
        this.access = access;
        this.gateway = gateway;
        this.policy = policy;
        this.fees = fees;
        this.health = health;
        this.verifier = verifier;
        this.storage = storage;
        this.audit = audit;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
    }

    private record Prepared(UUID id, UUID ref, String planName, int amountXof, int chargedXof, String customerName, String email, String phone) {}

    // ── Création ────────────────────────────────────────────────────

    public PurchaseResponse create(PurchaseRequest req) {
        PaymentOptions options = policy.optionsFor(PayableService.SIMULATOR, false);
        OrderChannel channel = req.channel() != null ? req.channel() : (options.online() ? OrderChannel.ONLINE : OrderChannel.MANUAL);
        if (channel == OrderChannel.ONLINE && !options.online()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, options.reason() != null ? options.reason() : "Le paiement en ligne n'est pas disponible.");
        }
        if (channel == OrderChannel.MANUAL && !options.manual()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le paiement manuel n'est pas proposé pour ce service.");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (DisposableEmails.isDisposable(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Utilisez une adresse e-mail durable : l'accès vous y est envoyé.");
        }
        Prepared p = tx.execute(s -> prepare(req, email, channel));
        if (channel == OrderChannel.MANUAL) return new PurchaseResponse(p.ref(), null, OrderChannel.MANUAL);

        CheckoutSession session;
        try {
            int fee = p.chargedXof() - p.amountXof();
            session = gateway.createCheckout(new CheckoutRequest(p.ref().toString(), "Simulateur PL-300 — " + p.planName(),
                    "Accès au simulateur de certification PL-300, forfait " + p.planName() + (fee > 0 ? " (dont " + fee + " FCFA de frais de paiement en ligne)" : ""),
                    p.chargedXof(), p.customerName(), p.email(), p.phone(),
                    frontendUrl + "/simulateur-pl-300/paiement?ref=" + p.ref(), frontendUrl + "/simulateur-pl-300/paiement?ref=" + p.ref() + "&annule=1"));
        } catch (GatewayException e) {
            health.recordFailure();
            tx.executeWithoutResult(s -> close(orders.findById(p.id()).orElseThrow(), OrderStatus.FAILED, "Création du paiement impossible : " + e.getMessage()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Le paiement en ligne est momentanément indisponible : vous pouvez payer par Wave, Orange Money ou virement.");
        }
        health.recordSuccess();
        tx.executeWithoutResult(s -> {
            SimOrder o = orders.findById(p.id()).orElseThrow();
            o.setProviderToken(session.token());
            o.setCheckoutUrl(session.url());
            orders.save(o);
        });
        return new PurchaseResponse(p.ref(), session.url(), OrderChannel.ONLINE);
    }

    private Prepared prepare(PurchaseRequest req, String email, OrderChannel channel) {
        SimPlan plan = plans.findById(req.plan()).filter(SimPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Ce forfait n'est pas disponible à la vente."));
        Learner learner;
        try {
            learner = learnerService.findOrCreateWithoutInvitation(req.firstName(), req.lastName(), email, req.phone());
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette adresse ne peut pas être utilisée pour un achat en ligne. Contactez-nous.");
        }
        if (orders.countByLearnerIdAndStatusAndCreatedAtAfter(learner.getId(), OrderStatus.PENDING, LocalDateTime.now().minusHours(1)) >= MAX_PENDING_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop de paiements en attente : patientez avant de réessayer.");
        }
        int price = plan.getPriceXof();
        // Les frais ne portent que sur le paiement en ligne ; le paiement manuel reste au prix du catalogue.
        int charged = channel == OrderChannel.ONLINE ? (int) fees.onlineTotal(price) : price;

        SimOrder o = new SimOrder();
        o.setLearner(learner);
        o.setPlanCode(plan.getCode());
        o.setPlanName(plan.getName());
        o.setAmountXof(price);
        o.setFeeXof(charged - price);
        o.setChargedXof(charged);
        o.setDurationDays(plan.getDurationDays());
        o.setExamAttempts(plan.getExamAttempts());
        o.setChannel(channel);
        o.setProvider(channel == OrderChannel.ONLINE ? gateway.provider() : "MANUAL");
        o.setProviderMode(channel == OrderChannel.ONLINE ? gateway.mode() : null);
        orders.save(o);
        return new Prepared(o.getId(), o.getPublicRef(), plan.getName(), price, charged, learner.getFullName(), email,
                req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
    }

    // ── Paiement en ligne : vérification (notification, retour du client, rapprochement, administrateur) ─────

    /** Notification du prestataire : le contenu reçu n'est qu'un déclencheur, l'état est relu auprès de lui. Jeton inconnu = ignoré. */
    public boolean onNotification(String providerToken) {
        if (providerToken == null || providerToken.isBlank()) return false;
        var order = orders.findByProviderToken(providerToken.trim());
        order.ifPresent(o -> settle(o.getId()));
        return order.isPresent();
    }

    /** Relit l'état du paiement auprès du prestataire et applique le résultat, une seule fois. Sans effet sur une commande manuelle. */
    public SimOrder settle(UUID orderId) {
        return tx.execute(s -> settleLocked(orderId));
    }

    private SimOrder settleLocked(UUID id) {
        SimOrder o = orders.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Commande", "id", id));
        if (o.getStatus().isFinal() || o.getChannel() != OrderChannel.ONLINE || o.getProviderToken() == null) return o;
        o.setLastCheckedAt(LocalDateTime.now());
        Verdict v = verifier.verify(o.getProviderToken(), o.getChargedXof(), o.getPublicRef().toString());
        switch (v.outcome()) {
            case PAID -> {
                o.setReceiptUrl(v.payment().receiptUrl());
                open(o, "payé en ligne", null);
            }
            case MISMATCH -> {
                // L'argent a peut-être été encaissé : on n'ouvre rien automatiquement, on alerte pour un contrôle humain.
                log.error("Paiement {} confirmé par le prestataire mais incohérent ({}) : à contrôler manuellement.", o.getPublicRef(), v.detail());
                close(o, OrderStatus.FAILED, "Vérification impossible (" + v.detail() + ") : à contrôler manuellement.");
            }
            case CANCELLED -> close(o, OrderStatus.CANCELLED, v.detail());
            case FAILED -> close(o, OrderStatus.FAILED, v.detail());
            default -> { /* en attente, ou injoignable : la commande reste ouverte (rapprochement), jusqu'à expiration */ }
        }
        return orders.save(o);
    }

    /** Ouvre l'accès d'une commande dont le paiement est établi (en ligne vérifié, ou manuel confirmé), une seule fois. */
    private void open(SimOrder o, String how, String confirmedBy) {
        UUID grantId = access.grantPurchased(o.getLearner(), o.getPlanCode(), o.getDurationDays(), o.getExamAttempts(), o.getId(),
                "Achat (" + o.getPlanName() + ", " + how + ", commande " + o.getPublicRef() + ")",
                o.getChannel() == OrderChannel.ONLINE ? "paiement-en-ligne" : "paiement-manuel");
        o.setGrantId(grantId);
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(LocalDateTime.now());
        o.setFailureReason(null);
        if (confirmedBy != null) {
            o.setConfirmedBy(confirmedBy);
            o.setConfirmedAt(o.getPaidAt());
        }
        audit.record(AuditAction.SIM_ORDER_PAID, "SimOrder", o.getId(),
                "Forfait " + o.getPlanName() + " " + how + " par " + o.getLearner().getFullName() + " (" + o.getChargedXof() + " XOF)"
                        + (confirmedBy != null ? ", confirmé par " + confirmedBy : ""));
        events.publishEvent(new OrderPaid(o.getId()));
    }

    private void close(SimOrder o, OrderStatus status, String reason) {
        if (o.getStatus().isFinal()) return;
        o.setStatus(status);
        o.setFailureReason(reason);
        orders.save(o);
    }

    // ── Paiement manuel : déclaration de l'acheteur, puis confirmation de l'équipe ─────

    /** L'acheteur déclare avoir payé (moyen + référence de l'opération). La commande passe à « déclarée » : l'équipe vérifie puis confirme. */
    public OrderStatusView declare(UUID ref, DeclareRequest req) {
        SimOrder o = tx.execute(s -> {
            SimOrder locked = orders.lockById(requireByRef(ref).getId()).orElseThrow();
            requireManual(locked);
            if (locked.getStatus() == OrderStatus.DECLARED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Votre paiement a déjà été déclaré : nous le vérifions.");
            }
            if (locked.getStatus() != OrderStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette commande n'attend plus de paiement.");
            }
            locked.setManualMethod(req.method());
            locked.setManualReference(req.reference().trim());
            locked.setDeclaredAt(LocalDateTime.now());
            locked.setStatus(OrderStatus.DECLARED);
            locked.setFailureReason(null);
            audit.record(AuditAction.SIM_ORDER_DECLARED, "SimOrder", locked.getId(),
                    "Paiement " + req.method() + " déclaré par " + locked.getLearner().getFullName() + " pour le forfait " + locked.getPlanName()
                            + " (" + locked.getChargedXof() + " XOF), référence « " + req.reference().trim() + " »");
            return orders.save(locked);
        });
        return view(o);
    }

    /** Justificatif du paiement manuel (capture ou photo) : pièce privée, lien signé et de courte durée pour l'équipe. */
    public OrderStatusView uploadProof(UUID ref, MultipartFile file) {
        SimOrder current = requireByRef(ref);
        requireManual(current);
        if (current.getStatus() != OrderStatus.PENDING && current.getStatus() != OrderStatus.DECLARED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette commande n'accepte plus de justificatif.");
        }
        String previous = current.getProofObjectKey();
        String key = storage.uploadPrivateImage(file, "sim-order-proofs").objectKey();
        SimOrder saved = tx.execute(s -> {
            SimOrder o = orders.findById(current.getId()).orElseThrow();
            o.setProofObjectKey(key);
            return orders.save(o);
        });
        if (previous != null && !previous.isBlank()) {
            try {
                storage.delete(previous);
            } catch (RuntimeException e) {
                log.warn("Suppression de l'ancien justificatif impossible ({}): {}", previous, e.getMessage());
            }
        }
        return view(orders.findByPublicRef(saved.getPublicRef()).orElseThrow());
    }

    /** Confirmation par l'équipe après avoir vu l'argent : ouvre l'accès, comme un paiement en ligne vérifié. */
    public OrderView confirm(UUID id, String actor) {
        tx.executeWithoutResult(s -> {
            SimOrder o = orders.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Commande", "id", id));
            requireManual(o);
            if (o.getStatus() != OrderStatus.DECLARED && o.getStatus() != OrderStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette commande ne peut pas être confirmée (statut : " + o.getStatus() + ").");
            }
            open(o, "payé manuellement", actor);
            orders.save(o);
        });
        return tx.execute(s -> adminView(orders.findDetailedById(id).orElseThrow()));
    }

    /** Refus d'un paiement déclaré (montant ou justificatif non conforme) : l'acheteur peut déclarer à nouveau. */
    public OrderView reject(UUID id, String reason, String actor) {
        tx.executeWithoutResult(s -> {
            SimOrder o = orders.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Commande", "id", id));
            requireManual(o);
            if (o.getStatus() != OrderStatus.DECLARED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seul un paiement déclaré peut être refusé (statut : " + o.getStatus() + ").");
            }
            o.setStatus(OrderStatus.PENDING);
            o.setFailureReason(reason.trim());
            o.setManualMethod(null);
            o.setManualReference(null);
            o.setDeclaredAt(null);
            audit.record(AuditAction.SIM_ORDER_REJECTED, "SimOrder", o.getId(),
                    "Paiement manuel de " + o.getLearner().getFullName() + " refusé par " + actor + " : " + reason.trim());
            orders.save(o);
        });
        return tx.execute(s -> adminView(orders.findDetailedById(id).orElseThrow()));
    }

    /** Lien signé (10 minutes) vers le justificatif, pour l'équipe habilitée. */
    public String proofLink(UUID id) {
        SimOrder o = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Commande", "id", id));
        if (o.getProofObjectKey() == null || o.getProofObjectKey().isBlank()) throw new ResourceNotFoundException("Justificatif", "commande", id);
        return storage.presignedGetUrl(o.getProofObjectKey(), PROOF_LINK_MINUTES);
    }

    private SimOrder requireByRef(UUID ref) {
        return orders.findByPublicRef(ref).orElseThrow(() -> new ResourceNotFoundException("Commande", "ref", ref));
    }

    private static void requireManual(SimOrder o) {
        if (o.getChannel() != OrderChannel.MANUAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette commande se règle en ligne : aucune déclaration manuelle n'est nécessaire.");
        }
    }

    // ── Suivi public ────────────────────────────────────────────────

    /** Suivi public d'une commande à partir de sa référence opaque ; relit le prestataire si le paiement en ligne est encore en attente. */
    public OrderStatusView status(UUID ref) {
        SimOrder o = requireByRef(ref);
        if (o.getStatus() == OrderStatus.PENDING && o.getChannel() == OrderChannel.ONLINE && o.getProviderToken() != null
                && (o.getLastCheckedAt() == null || o.getLastCheckedAt().isBefore(LocalDateTime.now().minus(RECHECK_DELAY)))) {
            o = settle(o.getId());
        }
        return view(o);
    }

    private static OrderStatusView view(SimOrder o) {
        // Le motif n'est montré qu'à l'acheteur d'un paiement manuel refusé (pour qu'il corrige) : jamais le détail d'un échec en ligne.
        String note = o.getChannel() == OrderChannel.MANUAL && o.getStatus() == OrderStatus.PENDING ? o.getFailureReason() : null;
        return new OrderStatusView(o.getPublicRef(), o.getStatus(), o.getPlanCode(), o.getPlanName(), o.getAmountXof(), o.getChargedXof(), o.getFeeXof(),
                o.getChannel(), maskEmail(o.getLearner().getEmail()), o.getStatus() == OrderStatus.PAID ? o.getReceiptUrl() : null,
                o.getManualMethod(), o.getProofObjectKey() != null, note);
    }

    // ── Rapprochement ───────────────────────────────────────────────

    /**
     * Filet de sécurité : revérifie les paiements en ligne restés en attente (notification perdue, client parti avant le retour) et abandonne
     * les commandes jamais payées (48 h en ligne, 7 jours en manuel). Une commande manuelle déjà déclarée n'expire jamais : elle attend l'équipe.
     */
    @Scheduled(fixedDelayString = "${app.simulator.orders-sweep-ms:300000}", initialDelayString = "${app.simulator.orders-sweep-ms:300000}")
    public void reconcile() {
        List<SimOrder> pending = orders.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PENDING, LocalDateTime.now().minusMinutes(2), PageRequest.of(0, 100));
        for (SimOrder o : pending) {
            try {
                boolean online = o.getChannel() == OrderChannel.ONLINE;
                SimOrder after = online ? settle(o.getId()) : o;
                Duration limit = online ? ABANDON_ONLINE_AFTER : ABANDON_MANUAL_AFTER;
                if (after.getStatus() == OrderStatus.PENDING && after.getCreatedAt().isBefore(LocalDateTime.now().minus(limit))) {
                    tx.executeWithoutResult(s -> orders.lockById(o.getId()).ifPresent(l -> close(l, OrderStatus.EXPIRED, "Paiement non finalisé (commande abandonnée)")));
                }
            } catch (RuntimeException e) {
                log.warn("Rapprochement de la commande {} impossible : {}", o.getPublicRef(), e.getMessage());
            }
        }
        if (!pending.isEmpty()) log.info("{} commande(s) du simulateur revérifiée(s)", pending.size());
    }

    // ── Administration ──────────────────────────────────────────────

    public Page<OrderView> list(OrderStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        return tx.execute(s -> (status == null ? orders.findAllByOrderByCreatedAtDesc(pageable) : orders.findAllByStatusOrderByCreatedAtDesc(status, pageable))
                .map(OrderService::adminView));
    }

    /** Revérification manuelle par un gestionnaire (paiement encaissé mais commande restée en attente). */
    public OrderView verify(UUID id, String actor) {
        SimOrder o = settle(id);
        audit.record(AuditAction.SIM_ORDER_VERIFIED, "SimOrder", id, "Commande " + o.getPublicRef() + " revérifiée par " + actor + " : " + o.getStatus());
        return tx.execute(s -> adminView(orders.findDetailedById(id).orElseThrow()));
    }

    private static OrderView adminView(SimOrder o) {
        return new OrderView(o.getId(), o.getPublicRef(), o.getLearner().getEmail(), o.getLearner().getFullName(), o.getPlanCode(), o.getPlanName(),
                o.getAmountXof(), o.getFeeXof(), o.getChargedXof(), o.getChannel(), o.getStatus(), o.getProvider(), o.getProviderMode(), o.getProviderToken(),
                o.getFailureReason(), o.getManualMethod(), o.getManualReference(), o.getDeclaredAt(), o.getProofObjectKey() != null, o.getConfirmedBy(),
                o.getGrantId(), o.getCreatedAt(), o.getPaidAt());
    }

    /** « awa.diop@gmail.com » → « a***@gmail.com » : indique où regarder sans révéler l'adresse à qui connaît la référence. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}
