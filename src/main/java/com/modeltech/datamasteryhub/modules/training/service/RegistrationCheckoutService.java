package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
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
import com.modeltech.datamasteryhub.modules.training.dto.response.OnlineCheckoutPayloads.Amounts;
import com.modeltech.datamasteryhub.modules.training.dto.response.OnlineCheckoutPayloads.CheckoutView;
import com.modeltech.datamasteryhub.modules.training.dto.response.OnlineCheckoutPayloads.OnlineOptions;
import com.modeltech.datamasteryhub.modules.training.dto.response.OnlineCheckoutPayloads.StartResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.PaymentCheckout;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutScope;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import com.modeltech.datamasteryhub.modules.training.enums.PayerType;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentCheckoutRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Paiement en ligne d'une inscription : une échéance, ou <b>tout le solde restant d'un coup</b>. Le payeur arrive par son lien de paiement
 * personnel (le jeton de l'échéance est son seul secret), est envoyé chez le prestataire, puis les échéances couvertes sont confirmées
 * <b>automatiquement</b>, mais seulement après avoir relu le paiement chez le prestataire (voir {@link OnlineCheckoutVerifier}).
 *
 * <p>La validation humaine de la candidature reste en amont : le lien n'existe qu'une fois l'inscription acceptée par l'équipe. Les entreprises
 * (facture, bon de commande) restent en paiement manuel. Si le prestataire encaisse alors qu'une échéance couverte a entre-temps été réglée ou
 * annulée, rien n'est confirmé automatiquement : la tentative passe en « Échec » avec une alerte pour un contrôle humain.
 */
@Service
@Slf4j
public class RegistrationCheckoutService {

    private static final Set<RegistrationStatus> PAYABLE = Set.of(
            RegistrationStatus.PAYMENT_PENDING, RegistrationStatus.PAYMENT_TO_CONFIRM, RegistrationStatus.CONFIRMED);
    private static final int MAX_ATTEMPTS_PER_HOUR = 8;
    private static final Duration REUSE_WINDOW = Duration.ofMinutes(30);
    private static final Duration RECHECK_DELAY = Duration.ofSeconds(3);
    private static final Duration ABANDON_AFTER = Duration.ofHours(48);

    private final PaymentRepository payments;
    private final PaymentCheckoutRepository checkouts;
    private final PaymentService paymentService;
    private final PaymentGateway gateway;
    private final PaymentPolicy policy;
    private final PaymentFees fees;
    private final GatewayHealth health;
    private final OnlineCheckoutVerifier verifier;
    private final AuditService audit;
    private final TransactionTemplate tx;

    @Value("${app.frontend.url:http://localhost:8088}")
    private String frontendUrl;

    @Value("${app.frontend.payment-path:/paiement}")
    private String paymentPath;

    public RegistrationCheckoutService(PaymentRepository payments, PaymentCheckoutRepository checkouts, PaymentService paymentService,
                                       PaymentGateway gateway, PaymentPolicy policy, PaymentFees fees, GatewayHealth health,
                                       OnlineCheckoutVerifier verifier, AuditService audit, PlatformTransactionManager txManager) {
        this.payments = payments;
        this.checkouts = checkouts;
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.policy = policy;
        this.fees = fees;
        this.health = health;
        this.verifier = verifier;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── Moyens offerts pour un lien de paiement ─────────────────────

    /** Moyens de paiement et montants à afficher sur la page du lien. Lecture seule, sans effet. */
    public OnlineOptions optionsFor(String token) {
        return tx.execute(s -> {
            Payment payment = byToken(token);
            Registration reg = payment.getRegistration();
            PaymentOptions o = policy.optionsFor(PayableService.REGISTRATION, reg.getPayerType() == PayerType.COMPANY);
            Amounts installment = payment.getStatus() == PaymentStatus.PENDING ? amounts(List.of(payment)) : null;
            List<Payment> open = openInstallments(reg.getId());
            Amounts balance = open.size() >= 2 ? amounts(open) : null;
            return new OnlineOptions(o.online(), o.manual(), o.fallback(), o.reason(), installment, balance);
        });
    }

    /** Le paiement manuel (déclaration) est-il proposé pour ce payeur ? Vrai tant que le manuel est voulu ou que l'en ligne est indisponible. */
    public boolean manualOffered(Registration reg) {
        return policy.optionsFor(PayableService.REGISTRATION, reg.getPayerType() == PayerType.COMPANY).manual();
    }

    private Amounts amounts(List<Payment> items) {
        long base = items.stream().mapToLong(Payment::getAmount).sum();
        long total = fees.onlineTotal(base);
        return new Amounts(base, total - base, total, items.size());
    }

    private List<Payment> openInstallments(UUID registrationId) {
        return payments.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(registrationId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).toList();
    }

    // ── Démarrage ───────────────────────────────────────────────────

    private record Prepared(UUID id, UUID ref, String itemName, String description, long chargedXof, String name, String email, String phone, String link) {}

    /** Crée (ou reprend) la tentative de paiement en ligne et renvoie l'adresse de la page du prestataire. */
    public StartResponse start(String token, CheckoutScope scope) {
        Object[] result = new Object[2];   // [StartResponse déjà prête | Prepared à envoyer]
        tx.executeWithoutResult(s -> {
            Payment payment = byToken(token);
            Registration reg = payment.getRegistration();
            if (!PAYABLE.contains(reg.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette inscription n'attend plus de paiement.");
            }
            PaymentOptions options = policy.optionsFor(PayableService.REGISTRATION, reg.getPayerType() == PayerType.COMPANY);
            if (!options.online()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        options.reason() != null ? options.reason() : "Le paiement en ligne n'est pas proposé pour cette inscription.");
            }
            List<Payment> items = scope == CheckoutScope.BALANCE ? openInstallments(reg.getId()) : List.of(payment);
            if (scope == CheckoutScope.INSTALLMENT && payment.getStatus() != PaymentStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, payment.getStatus() == PaymentStatus.DECLARED
                        ? "Votre paiement a déjà été déclaré : nous le vérifions." : "Cette échéance n'est plus à régler.");
            }
            if (items.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Il ne reste rien à régler sur cette inscription.");
            }
            Set<UUID> ids = items.stream().map(Payment::getId).collect(Collectors.toSet());

            // Même demande dans la demi-heure : on reprend le même lien plutôt que d'en créer un second (évite un double paiement).
            for (PaymentCheckout open : checkouts.findByRegistrationIdAndStatusOrderByCreatedAtDesc(reg.getId(), CheckoutStatus.PENDING)) {
                Set<UUID> covered = open.getPayments().stream().map(Payment::getId).collect(Collectors.toSet());
                if (open.getCheckoutUrl() != null && covered.equals(ids) && open.getCreatedAt().isAfter(LocalDateTime.now().minus(REUSE_WINDOW))) {
                    result[0] = new StartResponse(open.getPublicRef(), open.getCheckoutUrl());
                    return;
                }
            }
            if (checkouts.countByRegistrationIdAndCreatedAtAfter(reg.getId(), LocalDateTime.now().minusHours(1)) >= MAX_ATTEMPTS_PER_HOUR) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives de paiement : patientez avant de réessayer.");
            }

            long base = items.stream().mapToLong(Payment::getAmount).sum();
            long charged = fees.onlineTotal(base);
            PaymentCheckout c = new PaymentCheckout();
            c.setRegistration(reg);
            c.setScope(scope);
            c.setAmountXof(base);
            c.setFeeXof(charged - base);
            c.setChargedXof(charged);
            c.setProvider(gateway.provider());
            c.setProviderMode(gateway.mode());
            c.getPayments().addAll(items);
            checkouts.save(c);

            String title = reg.getBootcampTitle() != null ? reg.getBootcampTitle() : "Formation";
            String what = scope == CheckoutScope.BALANCE ? "solde de l'inscription" : "échéance " + payment.getInstallmentNumber() + "/" + payment.getInstallmentCount();
            long fee = charged - base;
            String back = frontendUrl + paymentPath + "/" + token + "?checkout=" + c.getPublicRef();
            result[1] = new Prepared(c.getId(), c.getPublicRef(), "Model Technologie — " + title,
                    title + " : " + what + (fee > 0 ? " (dont " + fee + " FCFA de frais de paiement en ligne)" : ""),
                    charged, (reg.getFirstName() + " " + (reg.getLastName() == null ? "" : reg.getLastName())).trim(), reg.getEmail(), reg.getPhone(), back);
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
            PaymentCheckout c = checkouts.findById(p.id()).orElseThrow();
            c.setProviderToken(session.token());
            c.setCheckoutUrl(session.url());
            checkouts.save(c);
        });
        return new StartResponse(p.ref(), session.url());
    }

    // ── Vérification (notification, retour du payeur, rapprochement) ─────

    /** Notification du prestataire : simple déclencheur, l'état est relu chez lui. Retourne faux si le jeton n'est pas celui d'une inscription. */
    public boolean onNotification(String providerToken) {
        if (providerToken == null || providerToken.isBlank()) return false;
        var found = checkouts.findByProviderToken(providerToken.trim());
        found.ifPresent(c -> settle(c.getId()));
        return found.isPresent();
    }

    public PaymentCheckout settle(UUID id) {
        return tx.execute(s -> settleLocked(id));
    }

    private PaymentCheckout settleLocked(UUID id) {
        PaymentCheckout c = checkouts.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Paiement en ligne", "id", id));
        if (c.getStatus().isFinal() || c.getProviderToken() == null) return c;
        c.setLastCheckedAt(LocalDateTime.now());
        Verdict v = verifier.verify(c.getProviderToken(), c.getChargedXof(), c.getPublicRef().toString());
        switch (v.outcome()) {
            case PAID -> complete(c, v.payment().receiptUrl());
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

    private void complete(PaymentCheckout c, String receiptUrl) {
        List<Payment> items = c.getPayments().stream().sorted(Comparator.comparing(Payment::getInstallmentNumber)).toList();
        Registration reg = c.getRegistration();
        boolean confirmable = PAYABLE.contains(reg.getStatus())
                && items.stream().allMatch(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.DECLARED);
        if (!confirmable) {
            // L'argent est encaissé mais ce qu'il devait régler l'est déjà (ou n'existe plus) : un humain tranche (remboursement, report…).
            log.error("Paiement en ligne {} encaissé ({} XOF) mais les échéances couvertes ne sont plus à régler : à contrôler manuellement.",
                    c.getPublicRef(), c.getChargedXof());
            flag(c, "Paiement encaissé alors que les échéances couvertes sont déjà réglées, annulées ou l'inscription n'est plus active : à contrôler manuellement.");
            return;
        }
        for (Payment p : items) paymentService.confirmOnline(p.getId(), c.getPublicRef().toString());
        c.setReceiptUrl(receiptUrl);
        c.setStatus(CheckoutStatus.PAID);
        c.setPaidAt(LocalDateTime.now());
        c.setFailureReason(null);
    }

    private void flag(PaymentCheckout c, String reason) {
        close(c, CheckoutStatus.FAILED, reason);
        audit.record(AuditAction.PAYMENT_ONLINE_MISMATCH, "PaymentCheckout", c.getId(),
                "Paiement en ligne de " + c.getChargedXof() + " XOF pour " + c.getRegistration().getFirstName() + " " + c.getRegistration().getLastName() + " : " + reason);
    }

    private void close(PaymentCheckout c, CheckoutStatus status, String reason) {
        if (c.getStatus().isFinal()) return;
        c.setStatus(status);
        c.setFailureReason(reason);
        checkouts.save(c);
    }

    // ── Suivi au retour du prestataire ──────────────────────────────

    /** Suivi public d'une tentative à partir de sa référence opaque ; relit le prestataire si elle est encore en attente. */
    public CheckoutView status(UUID ref) {
        PaymentCheckout c = checkouts.findByPublicRef(ref).orElseThrow(() -> new ResourceNotFoundException("Paiement en ligne", "ref", ref));
        if (c.getStatus() == CheckoutStatus.PENDING && c.getProviderToken() != null
                && (c.getLastCheckedAt() == null || c.getLastCheckedAt().isBefore(LocalDateTime.now().minus(RECHECK_DELAY)))) {
            settle(c.getId());
            c = checkouts.findByPublicRef(ref).orElseThrow();
        }
        return view(c);
    }

    private static CheckoutView view(PaymentCheckout c) {
        return new CheckoutView(c.getPublicRef(), c.getStatus(), c.getScope(), c.getAmountXof(), c.getFeeXof(), c.getChargedXof(), c.getPayments().size(),
                c.getStatus() == CheckoutStatus.PAID ? c.getReceiptUrl() : null);
    }

    // ── Rapprochement ───────────────────────────────────────────────

    /** Filet de sécurité : revérifie les tentatives restées en attente et abandonne celles jamais payées au bout de 48 h. */
    @Scheduled(fixedDelayString = "${app.payments.checkouts-sweep-ms:300000}", initialDelayString = "${app.payments.checkouts-sweep-ms:300000}")
    public void reconcile() {
        List<PaymentCheckout> pending = checkouts.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                CheckoutStatus.PENDING, LocalDateTime.now().minusMinutes(2), PageRequest.of(0, 100));
        for (PaymentCheckout c : pending) {
            try {
                PaymentCheckout after = settle(c.getId());
                if (after.getStatus() == CheckoutStatus.PENDING && after.getCreatedAt().isBefore(LocalDateTime.now().minus(ABANDON_AFTER))) {
                    tx.executeWithoutResult(s -> checkouts.lockById(c.getId()).ifPresent(l -> close(l, CheckoutStatus.EXPIRED, "Paiement non finalisé (tentative abandonnée)")));
                }
            } catch (RuntimeException e) {
                log.warn("Rapprochement du paiement en ligne {} impossible : {}", c.getPublicRef(), e.getMessage());
            }
        }
        if (!pending.isEmpty()) log.info("{} paiement(s) en ligne d'inscription revérifié(s)", pending.size());
    }

    private Payment byToken(String token) {
        Payment payment = payments.findByPublicTokenAndIsDeletedFalse(token).orElseThrow(() -> new ResourceNotFoundException("Lien de paiement invalide."));
        if (payment.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Ce lien de paiement a expiré. Contactez Model Technologie pour en recevoir un nouveau.");
        }
        return payment;
    }
}
