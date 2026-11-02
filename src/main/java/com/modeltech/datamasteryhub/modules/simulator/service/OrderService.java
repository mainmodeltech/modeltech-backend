package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.service.LearnerService;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.OrderStatusView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.OrderView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PurchaseRequest;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PurchaseResponse;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimOrder;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimPlan;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.CheckoutRequest;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.CheckoutSession;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.GatewayException;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway.GatewayPayment;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Commandes en ligne d'un forfait du simulateur.
 *
 * <p><b>Règle d'or : un accès ne s'ouvre jamais sur la foi du navigateur ni d'une notification.</b> Une commande ne devient
 * « payée » qu'après avoir interrogé le prestataire (jeton → statut, montant, référence), sous verrou de ligne : une notification
 * reçue deux fois, ou en même temps qu'un contrôle, ouvre un seul accès (et la base interdit un second accès par commande).
 * Les appels au prestataire se font hors de toute transaction ouverte à la création ; la vérification, elle, garde le verrou le
 * temps de l'appel (quelques secondes au plus, volume faible).
 */
@Service
@Slf4j
public class OrderService {

    private static final int MAX_PENDING_PER_HOUR = 5;
    private static final Duration RECHECK_DELAY = Duration.ofSeconds(3);
    private static final Duration ABANDON_AFTER = Duration.ofHours(48);

    private final SimPlanRepository plans;
    private final SimOrderRepository orders;
    private final LearnerService learnerService;
    private final AccessService access;
    private final PaymentGateway gateway;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    @Value("${app.frontend.url:http://localhost:8088}")
    private String frontendUrl;

    public OrderService(SimPlanRepository plans, SimOrderRepository orders, LearnerService learnerService, AccessService access,
                        PaymentGateway gateway, AuditService audit, ApplicationEventPublisher events, PlatformTransactionManager txManager) {
        this.plans = plans;
        this.orders = orders;
        this.learnerService = learnerService;
        this.access = access;
        this.gateway = gateway;
        this.audit = audit;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
    }

    private record Prepared(UUID id, UUID ref, String planName, int amountXof, String customerName, String email, String phone) {}

    // ── Création ────────────────────────────────────────────────────

    public PurchaseResponse create(PurchaseRequest req) {
        if (!gateway.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Le paiement en ligne n'est pas encore disponible.");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (DisposableEmails.isDisposable(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Utilisez une adresse e-mail durable : l'accès vous y est envoyé.");
        }
        Prepared p = tx.execute(s -> prepare(req, email));
        CheckoutSession session;
        try {
            session = gateway.createCheckout(new CheckoutRequest(p.ref().toString(), "Simulateur PL-300 — " + p.planName(),
                    "Accès au simulateur de certification PL-300, forfait " + p.planName(), p.amountXof(), p.customerName(), p.email(), p.phone(),
                    frontendUrl + "/simulateur-pl-300/paiement?ref=" + p.ref(), frontendUrl + "/simulateur-pl-300/paiement?ref=" + p.ref() + "&annule=1"));
        } catch (GatewayException e) {
            tx.executeWithoutResult(s -> close(orders.findById(p.id()).orElseThrow(), OrderStatus.FAILED, "Création du paiement impossible : " + e.getMessage()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Le paiement est momentanément indisponible. Réessayez dans quelques minutes.");
        }
        tx.executeWithoutResult(s -> {
            SimOrder o = orders.findById(p.id()).orElseThrow();
            o.setProviderToken(session.token());
            o.setCheckoutUrl(session.url());
            orders.save(o);
        });
        return new PurchaseResponse(p.ref(), session.url());
    }

    private Prepared prepare(PurchaseRequest req, String email) {
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
        SimOrder o = new SimOrder();
        o.setLearner(learner);
        o.setPlanCode(plan.getCode());
        o.setPlanName(plan.getName());
        o.setAmountXof(plan.getPriceXof());
        o.setDurationDays(plan.getDurationDays());
        o.setExamAttempts(plan.getExamAttempts());
        o.setProvider(gateway.provider());
        o.setProviderMode(gateway.mode());
        orders.save(o);
        return new Prepared(o.getId(), o.getPublicRef(), plan.getName(), plan.getPriceXof(), learner.getFullName(), email,
                req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
    }

    // ── Vérification (notification, retour du client, rapprochement, administrateur) ─────

    /** Notification du prestataire : le contenu reçu n'est qu'un déclencheur, l'état réel est relu auprès de lui. Jeton inconnu = ignoré. */
    public void onNotification(String providerToken) {
        if (providerToken == null || providerToken.isBlank()) return;
        orders.findByProviderToken(providerToken.trim()).ifPresentOrElse(o -> settle(o.getId()),
                () -> log.warn("Notification de paiement pour un jeton inconnu (ignorée)"));
    }

    /** Relit l'état du paiement auprès du prestataire et applique le résultat, une seule fois. */
    public SimOrder settle(UUID orderId) {
        return tx.execute(s -> settleLocked(orderId));
    }

    private SimOrder settleLocked(UUID id) {
        SimOrder o = orders.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Commande", "id", id));
        if (o.getStatus().isFinal() || o.getProviderToken() == null) return o;
        o.setLastCheckedAt(LocalDateTime.now());
        GatewayPayment p;
        try {
            p = gateway.fetchStatus(o.getProviderToken());
        } catch (GatewayException e) {
            log.warn("Vérification du paiement {} impossible pour l'instant : {}", o.getPublicRef(), e.getMessage());
            return orders.save(o);   // on réessaiera (rapprochement)
        }
        switch (p.state()) {
            case COMPLETED -> complete(o, p);
            case CANCELLED -> close(o, OrderStatus.CANCELLED, "Paiement annulé");
            case FAILED -> close(o, OrderStatus.FAILED, p.failReason() != null ? p.failReason() : "Paiement refusé");
            default -> { /* en attente, ou inconnu du prestataire : la commande reste ouverte jusqu'à expiration */ }
        }
        return orders.save(o);
    }

    private void complete(SimOrder o, GatewayPayment p) {
        List<String> problems = new ArrayList<>();
        if (p.totalAmount() != o.getAmountXof()) problems.add("montant reçu " + p.totalAmount() + " au lieu de " + o.getAmountXof());
        if (!o.getPublicRef().toString().equals(p.orderRef())) problems.add("référence de commande différente");
        if (!modeMatches(p.mode())) problems.add("mode « " + p.mode() + " » inattendu (attendu : " + gateway.mode() + ")");
        if (!problems.isEmpty()) {
            // L'argent a peut-être été encaissé : on n'ouvre rien automatiquement, on alerte pour un contrôle humain.
            log.error("Paiement {} confirmé par le prestataire mais incohérent ({}) : à contrôler manuellement.", o.getPublicRef(), String.join(" ; ", problems));
            close(o, OrderStatus.FAILED, "Vérification impossible (" + String.join(" ; ", problems) + ") : à contrôler manuellement.");
            return;
        }
        UUID grantId = access.grantPurchased(o.getLearner(), o.getPlanCode(), o.getDurationDays(), o.getExamAttempts(), o.getId(),
                "Achat en ligne (" + o.getPlanName() + ", commande " + o.getPublicRef() + ")");
        o.setGrantId(grantId);
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(LocalDateTime.now());
        o.setReceiptUrl(p.receiptUrl());
        audit.record(AuditAction.SIM_ORDER_PAID, "SimOrder", o.getId(),
                "Forfait " + o.getPlanName() + " payé en ligne par " + o.getLearner().getFullName() + " (" + o.getAmountXof() + " XOF)");
        events.publishEvent(new OrderPaid(o.getId()));
    }

    private boolean modeMatches(String providerMode) {
        if (providerMode == null || providerMode.isBlank()) return true;   // non communiqué : le jeton (préfixe « test_ ») et la clé suffisent
        String m = providerMode.trim().toLowerCase(Locale.ROOT);
        return gateway.mode().equals("live") ? m.equals("live") : (m.equals("test") || m.equals("sandbox"));
    }

    private void close(SimOrder o, OrderStatus status, String reason) {
        if (o.getStatus().isFinal()) return;
        o.setStatus(status);
        o.setFailureReason(reason);
        orders.save(o);
    }

    /** Suivi public d'une commande à partir de sa référence opaque ; relit le prestataire si le statut est encore en attente. */
    public OrderStatusView status(UUID ref) {
        SimOrder o = orders.findByPublicRef(ref).orElseThrow(() -> new ResourceNotFoundException("Commande", "ref", ref));
        if (o.getStatus() == OrderStatus.PENDING && o.getProviderToken() != null
                && (o.getLastCheckedAt() == null || o.getLastCheckedAt().isBefore(LocalDateTime.now().minus(RECHECK_DELAY)))) {
            o = settle(o.getId());
        }
        return new OrderStatusView(o.getPublicRef(), o.getStatus(), o.getPlanCode(), o.getPlanName(), o.getAmountXof(), maskEmail(o.getLearner().getEmail()),
                o.getStatus() == OrderStatus.PAID ? o.getReceiptUrl() : null);
    }

    // ── Rapprochement ───────────────────────────────────────────────

    /** Filet de sécurité : revérifie les commandes restées en attente (notification perdue, client parti avant le retour) et abandonne les plus anciennes. */
    @Scheduled(fixedDelayString = "${app.simulator.orders-sweep-ms:300000}", initialDelayString = "${app.simulator.orders-sweep-ms:300000}")
    public void reconcile() {
        List<SimOrder> pending = orders.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PENDING, LocalDateTime.now().minusMinutes(2), PageRequest.of(0, 100));
        for (SimOrder o : pending) {
            try {
                SimOrder after = settle(o.getId());
                if (after.getStatus() == OrderStatus.PENDING && after.getCreatedAt().isBefore(LocalDateTime.now().minus(ABANDON_AFTER))) {
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
        return tx.execute(s -> adminView(orders.findById(id).orElseThrow()));
    }

    private static OrderView adminView(SimOrder o) {
        return new OrderView(o.getId(), o.getPublicRef(), o.getLearner().getEmail(), o.getLearner().getFullName(), o.getPlanCode(), o.getPlanName(),
                o.getAmountXof(), o.getStatus(), o.getProvider(), o.getProviderMode(), o.getProviderToken(), o.getFailureReason(), o.getGrantId(),
                o.getCreatedAt(), o.getPaidAt());
    }

    /** « awa.diop@gmail.com » → « a***@gmail.com » : indique où regarder sans révéler l'adresse à qui connaît la référence. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}
