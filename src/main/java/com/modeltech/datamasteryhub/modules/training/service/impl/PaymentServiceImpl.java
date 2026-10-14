package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditFlag;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.audit.service.FourEyes;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.security.Actor;
import com.modeltech.datamasteryhub.common.util.TokenUtils;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.service.LearnerService;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.notification.service.PaymentNotice;
import com.modeltech.datamasteryhub.modules.training.dto.request.AcceptRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.DeclarePaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.ManualPaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.EnrollmentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.PublicPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PayerType;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.mapper.PaymentMapper;
import com.modeltech.datamasteryhub.modules.training.mapper.RegistrationMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationPricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final Set<RegistrationStatus> AWAITING_PAYMENT =
            Set.of(RegistrationStatus.PAYMENT_PENDING, RegistrationStatus.PAYMENT_TO_CONFIRM);

    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BootcampSessionRepository sessionRepository;
    private final AdminUserRepository adminUserRepository;
    private final LearnerService learnerService;
    private final RegistrationMapper registrationMapper;
    private final PaymentMapper paymentMapper;
    private final RegistrationPricing pricing;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final com.modeltech.datamasteryhub.modules.training.repository.InvoiceRepository invoiceRepository;
    private final AuditService audit;
    private final FourEyes fourEyes;

    /** Délai (jours) laissé pour payer quand l'admin ne fixe pas d'échéance. */
    @Value("${app.payment.default-due-days:2}")
    private int defaultDueDays;

    /** Durée de validité du lien de paiement, comptée à partir de l'échéance. */
    @Value("${app.payment.link-validity-days:30}")
    private int linkValidityDays;

    @Value("${app.payment.reminder-days:2}")
    private int reminderDays;

    @Value("${app.payment.max-reminders:3}")
    private int maxReminders;

    // =========================================================================
    //  CANDIDATURES (ADMIN)
    // =========================================================================

    @Override
    @Transactional
    public RegistrationResponse accept(UUID registrationId, AcceptRegistrationRequest request, String actor) {
        Registration reg = getRegistration(registrationId);
        if (reg.getStatus() != RegistrationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une candidature en attente peut être acceptée (statut actuel : " + reg.getStatus() + ").");
        }
        if (adminUserRepository.existsByEmailAndIsDeletedFalse(reg.getEmail().trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet e-mail est celui d'un compte de back-office : impossible de créer un compte apprenant.");
        }

        AcceptRegistrationRequest req = request != null ? request : new AcceptRegistrationRequest();
        long total = resolveTotal(reg, req);
        List<Slot> schedule = buildSchedule(total, req.getInstallments());

        LocalDateTime now = LocalDateTime.now();
        String currency = reg.getBootcamp() != null && reg.getBootcamp().getCurrency() != null
                ? reg.getBootcamp().getCurrency() : "XOF";
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < schedule.size(); i++) {
            Slot slot = schedule.get(i);
            Payment p = new Payment();
            p.setRegistration(reg);
            p.setAmount(slot.amount());
            p.setCurrency(currency);
            p.setInstallmentNumber(i + 1);
            p.setInstallmentCount(schedule.size());
            p.setDueDate(slot.dueDate());
            p.setPublicToken(TokenUtils.randomToken());
            LocalDateTime from = slot.dueDate().atStartOfDay().isAfter(now) ? slot.dueDate().atStartOfDay() : now;
            p.setTokenExpiresAt(from.plusDays(linkValidityDays));
            p.setInvoiceRef(blankToNull(req.getInvoiceRef()));
            p.setPurchaseOrderRef(blankToNull(req.getPurchaseOrderRef()));
            payments.add(p);
        }
        paymentRepository.saveAll(payments);

        Long quoted = pricing.quote(reg).map(RegistrationPricing.Quote::total).orElse(null);
        boolean priceOverride = quoted == null || quoted != total;
        reg.setStatus(RegistrationStatus.PAYMENT_PENDING);
        reg.setAcceptedAt(now);
        reg.setAcceptedBy(actor);
        reg.setPayerType(req.getPayerType() != null ? req.getPayerType() : PayerType.INDIVIDUAL);
        reg.setTotalAmount(total);
        Registration saved = registrationRepository.save(reg);

        notificationService.sendPaymentLinkEmail(notice(saved, payments.get(0), null), false);
        Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("montant", total);
        accepted.put("devise", currency);
        accepted.put("echeances", payments.size());
        if (quoted != null) accepted.put("tarifCatalogue", quoted);
        audit.record(AuditAction.REGISTRATION_ACCEPTED, "Registration", saved.getId(),
                "Candidature de " + person(saved) + " acceptée : " + total + " " + currency + " en " + payments.size() + " échéance(s)"
                        + (priceOverride ? " (montant différent du tarif du catalogue)" : ""),
                accepted, priceOverride ? AuditFlag.OVERRIDE : null);
        log.info("Candidature {} acceptée par {} : {} {} en {} échéance(s)",
                saved.getId(), actor, total, currency, payments.size());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RegistrationResponse rejectRegistration(UUID registrationId, String reason) {
        Registration reg = getRegistration(registrationId);
        if (reg.getStatus() != RegistrationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une candidature en attente peut être refusée (statut actuel : " + reg.getStatus() + ").");
        }
        reg.setStatus(RegistrationStatus.REJECTED);
        reg.setRejectedReason(reason.trim());
        Registration saved = registrationRepository.save(reg);
        audit.record(AuditAction.REGISTRATION_REJECTED, "Registration", saved.getId(),
                "Candidature de " + person(saved) + " refusée : " + reason.trim());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AdminPaymentResponse recordManualPayment(UUID registrationId, ManualPaymentRequest request) {
        Registration reg = getRegistration(registrationId);
        if (!AWAITING_PAYMENT.contains(reg.getStatus()) && reg.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette candidature n'a pas d'échéance à régler (statut : " + reg.getStatus() + ").");
        }
        List<Payment> payments = paymentRepository
                .findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(registrationId);
        Payment target = request.getInstallmentNumber() != null
                ? payments.stream().filter(p -> p.getInstallmentNumber().equals(request.getInstallmentNumber()))
                        .findFirst().orElseThrow(() -> new ResourceNotFoundException(
                                "Échéance " + request.getInstallmentNumber() + " introuvable pour cette inscription."))
                : payments.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "Aucune échéance en attente de paiement."));
        if (target.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "L'échéance " + target.getInstallmentNumber() + " n'est pas en attente (statut : "
                            + target.getStatus() + ").");
        }

        markDeclared(target, reg, request.getMethod(), blankToNull(request.getReference()));
        target.setRecordedBy(Actor.email());
        target.setInvoiceRef(firstNonBlank(request.getInvoiceRef(), target.getInvoiceRef()));
        target.setPurchaseOrderRef(firstNonBlank(request.getPurchaseOrderRef(), target.getPurchaseOrderRef()));
        target.setNotes(blankToNull(request.getNotes()));
        Payment saved = paymentRepository.save(target);
        audit.record(AuditAction.PAYMENT_RECORDED, "Payment", saved.getId(),
                "Paiement hors site saisi pour " + person(reg) + " : " + saved.getAmount() + " " + saved.getCurrency()
                        + " (échéance " + saved.getInstallmentNumber() + "/" + saved.getInstallmentCount() + ", " + request.getMethod() + ")",
                Map.of("registrationId", reg.getId().toString(), "montant", saved.getAmount(), "moyen", String.valueOf(request.getMethod())), null);
        return paymentMapper.toAdmin(saved);
    }

    private static final Set<RegistrationStatus> CANCELLABLE = Set.of(RegistrationStatus.PENDING,
            RegistrationStatus.PAYMENT_PENDING, RegistrationStatus.PAYMENT_TO_CONFIRM, RegistrationStatus.CONFIRMED);

    @Override
    @Transactional
    public RegistrationResponse cancelRegistration(UUID registrationId, String reason, String actor) {
        Registration reg = getRegistration(registrationId);
        if (!CANCELLABLE.contains(reg.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette inscription ne peut plus être annulée (statut : " + reg.getStatus() + ").");
        }
        closeRegistration(reg, reason.trim());
        audit.record(AuditAction.REGISTRATION_CANCELLED, "Registration", registrationId,
                "Inscription de " + person(reg) + " annulée : " + reason.trim());
        log.info("Inscription {} annulée par {} : {}", registrationId, actor, reason);
        return toResponse(reg);
    }

    /** Annulation : échéances ouvertes annulées ; si elle était confirmée, la place est libérée et l'accès fermé. */
    private void closeRegistration(Registration reg, String reason) {
        boolean wasConfirmed = reg.getStatus() == RegistrationStatus.CONFIRMED;
        reg.setStatus(RegistrationStatus.CANCELLED);
        reg.setCancelledAt(LocalDateTime.now());
        reg.setCancelledReason(reason);

        paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.DECLARED)
                .forEach(p -> {
                    p.setStatus(PaymentStatus.CANCELLED);
                    paymentRepository.save(p);
                });

        if (wasConfirmed) {
            BootcampSession session = reg.getSession();
            if (session != null) {
                session.setCurrentParticipants(Math.max(0, session.getCurrentParticipants() - 1));
                session.setIsFull(false);
                sessionRepository.save(session);
            }
            enrollmentRepository.findByRegistrationIdAndIsDeletedFalse(reg.getId()).ifPresent(e -> {
                e.setStatus(com.modeltech.datamasteryhub.modules.training.enums.EnrollmentStatus.CANCELLED);
                enrollmentRepository.save(e);
            });
        }
        registrationRepository.save(reg);
    }

    /**
     * Demande de remboursement. La demande est ensuite validée par <b>une autre personne</b> habilitée
     * ({@link #approveRefund}). Exception pour une petite équipe : si la personne qui demande est la seule à pouvoir valider,
     * le remboursement est effectué tout de suite et consigné « sans second contrôle ».
     */
    @Override
    @Transactional
    public AdminPaymentResponse refund(UUID paymentId, String reason, String actor) {
        Payment payment = getPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une échéance confirmée peut être remboursée (statut : " + payment.getStatus() + ").");
        }
        if (payment.getRefundRequestedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une demande de remboursement est déjà en attente de validation pour cette échéance.");
        }
        payment.setRefundRequestedAt(LocalDateTime.now());
        payment.setRefundRequestedBy(actor);
        payment.setRefundRequestReason(reason.trim());

        if (Actor.has(Permission.PAYMENTS_REFUND_APPROVE) && !fourEyes.otherHolderExists(Permission.PAYMENTS_REFUND_APPROVE, actor)) {
            payment.setRefundUnchecked(true);
            return executeRefund(payment, actor);
        }
        paymentRepository.save(payment);
        audit.record(AuditAction.REFUND_REQUESTED, "Payment", paymentId,
                "Remboursement demandé pour " + person(payment.getRegistration()) + " : " + payment.getAmount() + " " + payment.getCurrency()
                        + " — " + reason.trim() + " (en attente de validation)",
                refundDetails(payment), null);
        return paymentMapper.toAdmin(payment);
    }

    @Override
    @Transactional
    public AdminPaymentResponse approveRefund(UUID paymentId, String actor) {
        Payment payment = getPayment(paymentId);
        if (payment.getRefundRequestedAt() == null || payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucune demande de remboursement en attente pour cette échéance.");
        }
        if (FourEyes.same(payment.getRefundRequestedBy(), actor)) {
            if (fourEyes.otherHolderExists(Permission.PAYMENTS_REFUND_APPROVE, actor)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous avez demandé ce remboursement : une autre personne habilitée doit le valider.");
            }
            payment.setRefundUnchecked(true);
        }
        return executeRefund(payment, actor);
    }

    @Override
    @Transactional
    public AdminPaymentResponse declineRefund(UUID paymentId, String reason, String actor) {
        Payment payment = getPayment(paymentId);
        if (payment.getRefundRequestedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucune demande de remboursement en attente pour cette échéance.");
        }
        Map<String, Object> details = refundDetails(payment);
        details.put("motifDuRefus", reason.trim());
        String requester = payment.getRefundRequestedBy();
        payment.setRefundRequestedAt(null);
        payment.setRefundRequestedBy(null);
        payment.setRefundRequestReason(null);
        paymentRepository.save(payment);
        audit.record(AuditAction.REFUND_DECLINED, "Payment", paymentId,
                "Demande de remboursement de " + requester + " pour " + person(payment.getRegistration()) + " refusée : " + reason.trim(),
                details, null);
        return paymentMapper.toAdmin(payment);
    }

    /** Effectue le remboursement (paiement remboursé, accès fermé s'il ne reste plus rien de payé) et le consigne. */
    private AdminPaymentResponse executeRefund(Payment payment, String actor) {
        String reason = payment.getRefundRequestReason();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setRefundedBy(actor);
        payment.setRefundReason(reason);
        paymentRepository.save(payment);

        Registration reg = payment.getRegistration();
        boolean stillPaid = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId())
                .stream().anyMatch(p -> p.getStatus() == PaymentStatus.CONFIRMED);
        if (!stillPaid && reg.getStatus() == RegistrationStatus.CONFIRMED) {
            closeRegistration(reg, "Remboursement : " + reason);
        }
        boolean unchecked = payment.isRefundUnchecked();
        audit.record(AuditAction.REFUND_APPROVED, "Payment", payment.getId(),
                "Remboursement de " + payment.getAmount() + " " + payment.getCurrency() + " effectué pour " + person(reg)
                        + " — " + reason + (unchecked ? " (sans second contrôle : aucune autre personne habilitée)" : ""),
                refundDetails(payment), unchecked ? AuditFlag.NO_SECOND_CHECK : null);
        log.info("Échéance {} remboursée par {} : {}", payment.getId(), actor, reason);
        return paymentMapper.toAdmin(payment);
    }

    private Map<String, Object> refundDetails(Payment payment) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("registrationId", payment.getRegistration().getId().toString());
        d.put("montant", payment.getAmount());
        d.put("devise", payment.getCurrency());
        d.put("demandePar", payment.getRefundRequestedBy());
        d.put("motif", payment.getRefundRequestReason());
        return d;
    }

    private static String person(Registration reg) {
        return (reg.getFirstName() + " " + reg.getLastName()).trim();
    }

    // =========================================================================
    //  PAIEMENTS (ADMIN)
    // =========================================================================

    @Override
    public Page<AdminPaymentResponse> findAllForAdmin(PaymentStatus status, UUID registrationId, Pageable pageable) {
        return paymentRepository.search(status, registrationId, pageable).map(paymentMapper::toAdmin);
    }

    @Override
    @Transactional
    public AdminPaymentResponse confirm(UUID paymentId, String actor) {
        Payment payment = getPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.DECLARED && payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce paiement ne peut pas être confirmé (statut : " + payment.getStatus() + ").");
        }
        Registration reg = payment.getRegistration();
        if (reg.getStatus() == RegistrationStatus.CANCELLED || reg.getStatus() == RegistrationStatus.REJECTED
                || reg.getStatus() == RegistrationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "L'inscription n'est plus active (statut : " + reg.getStatus() + ").");
        }

        // Séparation des tâches : un paiement saisi hors site ne peut pas être confirmé par la personne qui l'a saisi
        boolean unchecked = false;
        if (FourEyes.same(payment.getRecordedBy(), actor)) {
            if (fourEyes.otherHolderExists(Permission.PAYMENTS_MANAGE, actor)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous avez saisi ce paiement : une autre personne habilitée doit le confirmer.");
            }
            unchecked = true;
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setConfirmedAt(now);
        payment.setConfirmedBy(actor);
        payment.setConfirmedUnchecked(unchecked);
        if (payment.getPaidAt() == null) payment.setPaidAt(payment.getDeclaredAt() != null ? payment.getDeclaredAt() : now);
        payment.setRejectionReason(null);
        paymentRepository.save(payment);

        if (AWAITING_PAYMENT.contains(reg.getStatus())) {
            openEnrollment(reg);
        }
        Map<String, Object> confirmed = new LinkedHashMap<>();
        confirmed.put("registrationId", reg.getId().toString());
        confirmed.put("montant", payment.getAmount());
        confirmed.put("moyen", String.valueOf(payment.getMethod()));
        if (payment.getRecordedBy() != null) confirmed.put("saisiPar", payment.getRecordedBy());
        audit.record(AuditAction.PAYMENT_CONFIRMED, "Payment", payment.getId(),
                "Paiement de " + payment.getAmount() + " " + payment.getCurrency() + " confirmé pour " + person(reg)
                        + (unchecked ? " (saisi et confirmé par la même personne : aucune autre personne habilitée)" : ""),
                confirmed, unchecked ? AuditFlag.NO_SECOND_CHECK : null);
        return paymentMapper.toAdmin(payment);
    }

    @Override
    @Transactional
    public AdminPaymentResponse rejectPayment(UUID paymentId, String reason) {
        Payment payment = getPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.DECLARED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un paiement déclaré peut être refusé (statut : " + payment.getStatus() + ").");
        }
        Registration reg = payment.getRegistration();

        PaymentNotice notice = notice(reg, payment, reason.trim());   // avant d'effacer méthode/référence
        deleteProofQuietly(payment);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setRejectionReason(reason.trim());
        payment.setMethod(null);
        payment.setReference(null);
        payment.setDeclaredAt(null);
        paymentRepository.save(payment);

        if (reg.getStatus() == RegistrationStatus.PAYMENT_TO_CONFIRM) {
            boolean otherDeclared = paymentRepository
                    .findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId()).stream()
                    .anyMatch(p -> p.getStatus() == PaymentStatus.DECLARED);
            if (!otherDeclared) {
                reg.setStatus(RegistrationStatus.PAYMENT_PENDING);
                registrationRepository.save(reg);
            }
        }
        notificationService.sendPaymentRejectedEmail(notice);
        audit.record(AuditAction.PAYMENT_REJECTED, "Payment", payment.getId(),
                "Paiement de " + payment.getAmount() + " " + payment.getCurrency() + " refusé pour " + person(reg) + " : " + reason.trim());
        return paymentMapper.toAdmin(payment);
    }

    @Override
    @Transactional
    public AdminPaymentResponse remind(UUID paymentId) {
        Payment payment = getPayment(paymentId);
        Registration reg = payment.getRegistration();
        boolean remindable = payment.getStatus() == PaymentStatus.PENDING
                && (reg.getStatus() == RegistrationStatus.PAYMENT_PENDING || reg.getStatus() == RegistrationStatus.CONFIRMED);
        if (!remindable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce paiement n'est pas en attente de règlement.");
        }
        sendReminder(payment, reg, LocalDateTime.now(), true);
        return paymentMapper.toAdmin(payment);
    }

    @Override
    public Page<EnrollmentResponse> findEnrollments(Pageable pageable) {
        return enrollmentRepository.findAllByIsDeletedFalse(pageable).map(paymentMapper::toEnrollment);
    }

    // =========================================================================
    //  LIEN DE PAIEMENT (PUBLIC)
    // =========================================================================

    @Override
    public PublicPaymentResponse getByToken(String token) {
        Payment payment = getPaymentByToken(token);
        return toPublic(payment);
    }

    @Override
    @Transactional
    public PublicPaymentResponse declare(String token, DeclarePaymentRequest request) {
        Payment payment = getPaymentByToken(token);
        Registration reg = payment.getRegistration();

        if (!paymentMapper.isPublicMethod(request.getMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Moyen de paiement non disponible : choisissez Wave, Orange Money ou virement.");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    payment.getStatus() == PaymentStatus.DECLARED
                            ? "Votre paiement a déjà été déclaré : nous le vérifions."
                            : "Ce paiement n'est plus à régler.");
        }
        if (reg.getStatus() != RegistrationStatus.PAYMENT_PENDING && reg.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette inscription n'attend plus de paiement.");
        }

        markDeclared(payment, reg, request.getMethod(), request.getReference().trim());
        paymentRepository.save(payment);
        notificationService.notifyPaymentDeclared(notice(reg, payment, null));
        return toPublic(payment);
    }

    @Override
    @Transactional
    public PublicPaymentResponse uploadProof(String token, MultipartFile file) {
        Payment payment = getPaymentByToken(token);
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.DECLARED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce paiement n'accepte plus de justificatif.");
        }
        String previousKey = payment.getProofObjectKey();
        // Pièce privée : pas d'URL publique en base, le lien est signé (courte durée) à chaque consultation admin.
        StorageService.UploadResult result = storageService.uploadPrivateImage(file, "payment-proofs");
        payment.setProofObjectKey(result.objectKey());
        payment.setProofUrl(null);
        paymentRepository.save(payment);
        if (previousKey != null && !previousKey.isBlank()) {
            try {
                storageService.delete(previousKey);
            } catch (RuntimeException e) {
                log.warn("Suppression de l'ancien justificatif impossible ({}): {}", previousKey, e.getMessage());
            }
        }
        return toPublic(payment);
    }

    // =========================================================================
    //  RELANCES
    // =========================================================================

    @Override
    @Transactional
    public int sendDueReminders(LocalDateTime now) {
        int sent = 0;
        for (Payment p : paymentRepository.findRemindable()) {
            if (p.getReminderCount() >= maxReminders || p.getTokenExpiresAt().isBefore(now)) continue;
            if (!now.isBefore(nextReminderAt(p))) {
                sendReminder(p, p.getRegistration(), now, !isFirstNotice(p));
                sent++;
            }
        }
        if (sent > 0) log.info("{} relance(s) de paiement envoyée(s)", sent);
        return sent;
    }

    /** Première relance : J+2 après l'envoi du lien (1re échéance) ou 2 jours avant l'échéance (suivantes). */
    private LocalDateTime nextReminderAt(Payment p) {
        if (p.getLastReminderAt() != null) return p.getLastReminderAt().plusDays(reminderDays);
        if (p.getInstallmentNumber() > 1 && p.getDueDate() != null) {
            return p.getDueDate().atStartOfDay().minusDays(reminderDays);
        }
        return p.getCreatedAt().plusDays(reminderDays);
    }

    /** Pour une échéance ultérieure, le premier e-mail est le lien lui-même, pas un « rappel ». */
    private boolean isFirstNotice(Payment p) {
        return p.getInstallmentNumber() > 1 && p.getReminderCount() == 0;
    }

    private void sendReminder(Payment p, Registration reg, LocalDateTime now, boolean asReminder) {
        notificationService.sendPaymentLinkEmail(notice(reg, p, null), asReminder);
        p.setReminderCount(p.getReminderCount() + 1);
        p.setLastReminderAt(now);
        paymentRepository.save(p);
    }

    // =========================================================================
    //  PRIVÉ
    // =========================================================================

    private record Slot(long amount, LocalDate dueDate) {}

    private long resolveTotal(Registration reg, AcceptRegistrationRequest req) {
        long total;
        if (req.getTotalAmount() != null) {
            total = req.getTotalAmount();
            // Un montant différent du tarif (remise, accord commercial) est une dérogation de prix : droit dédié
            Long quoted = pricing.quote(reg).map(RegistrationPricing.Quote::total).orElse(null);
            if (quoted == null || quoted != total) {
                Actor.require(Permission.PRICE_OVERRIDE, "le montant d'une inscription par rapport au tarif");
            }
        } else {
            total = pricing.quote(reg).map(RegistrationPricing.Quote::total)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Aucun prix numérique n'est renseigné pour cette formation : "
                                    + "saisissez-le sur la formation ou indiquez totalAmount."));
        }
        if (total <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant à payer est nul : confirmez directement l'inscription (PATCH /status).");
        }
        return total;
    }

    private List<Slot> buildSchedule(long total, List<AcceptRegistrationRequest.Installment> installments) {
        if (installments == null || installments.isEmpty()) {
            return List.of(new Slot(total, LocalDate.now().plusDays(defaultDueDays)));
        }
        int n = installments.size();
        for (int i = 1; i < n; i++) {
            if (installments.get(i).getDueDate().isBefore(installments.get(i - 1).getDueDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Les échéances doivent être classées par date croissante.");
            }
        }
        long withAmount = installments.stream().filter(x -> x.getAmount() != null).count();
        List<Slot> slots = new ArrayList<>();
        if (withAmount == 0) {
            long each = total / n;
            long remainder = total % n;
            if (each == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trop d'échéances pour ce montant.");
            }
            for (int i = 0; i < n; i++) {
                slots.add(new Slot(each + (i == 0 ? remainder : 0), installments.get(i).getDueDate()));
            }
        } else if (withAmount == n) {
            long sum = installments.stream().mapToLong(AcceptRegistrationRequest.Installment::getAmount).sum();
            if (sum != total) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La somme des échéances (" + sum + ") doit égaler le total (" + total + ").");
            }
            for (AcceptRegistrationRequest.Installment x : installments) {
                if (x.getAmount() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une échéance ne peut pas être nulle.");
                }
                slots.add(new Slot(x.getAmount(), x.getDueDate()));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indiquez le montant de toutes les échéances, ou d'aucune (répartition égale).");
        }
        return slots;
    }

    private void markDeclared(Payment payment, Registration reg,
                              com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod method,
                              String reference) {
        payment.setStatus(PaymentStatus.DECLARED);
        payment.setMethod(method);
        payment.setReference(reference);
        payment.setDeclaredAt(LocalDateTime.now());
        payment.setRejectionReason(null);
        if (reg.getStatus() == RegistrationStatus.PAYMENT_PENDING) {
            reg.setStatus(RegistrationStatus.PAYMENT_TO_CONFIRM);
            registrationRepository.save(reg);
        }
    }

    /**
     * Premier paiement confirmé : l'inscription devient CONFIRMED, la place est comptée, le compte
     * apprenant est créé (ou retrouvé par e-mail) avec son invitation, et l'accès à la session ouvert.
     * La place peut dépasser la capacité : un paiement déjà reçu n'est jamais refusé pour cause de session complète.
     */
    private void openEnrollment(Registration reg) {
        reg.setStatus(RegistrationStatus.CONFIRMED);
        BootcampSession session = reg.getSession();
        if (session != null) {
            session.setCurrentParticipants(session.getCurrentParticipants() + 1);
            session.setIsFull(session.getCurrentParticipants() >= session.getMaxParticipants());
            if (session.getCurrentParticipants() > session.getMaxParticipants()) {
                log.warn("Session {} sur-réservée : {}/{}", session.getSessionName(),
                        session.getCurrentParticipants(), session.getMaxParticipants());
            }
            sessionRepository.save(session);
            // L'e-mail de confirmation est envoyé hors transaction : on initialise ce qu'il lira.
            if (session.getBootcamp() != null) session.getBootcamp().getTitle();
        }

        Learner learner = learnerService.findOrCreateInvited(
                reg.getFirstName(), reg.getLastName(), reg.getEmail(), reg.getPhone(), reg.getCountry());
        reg.setLearner(learner);
        registrationRepository.save(reg);

        if (enrollmentRepository.findByRegistrationIdAndIsDeletedFalse(reg.getId()).isEmpty()) {
            Enrollment enrollment = new Enrollment();
            enrollment.setLearner(learner);
            enrollment.setRegistration(reg);
            enrollment.setSession(session);
            if (session != null) {
                enrollment.setAccessStartsAt(session.getStartDate());
                enrollment.setAccessEndsAt(session.getEndDate());
            }
            enrollmentRepository.save(enrollment);
        }
        // Compte déjà actif : une confirmation (les nouveaux comptes reçoivent l'invitation, qui vaut confirmation)
        if (learner.getPasswordHash() != null) notificationService.sendEnrollmentConfirmedEmail(reg);
        log.info("Inscription {} confirmée : compte apprenant {} et accès ouverts", reg.getId(), learner.getEmail());
    }

    private PublicPaymentResponse toPublic(Payment payment) {
        List<Payment> all = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(
                payment.getRegistration().getId());
        PublicPaymentResponse response = paymentMapper.toPublic(payment, all);
        response.setInvoiceAvailable(invoiceRepository.findByRegistrationIdAndStatusAndIsDeletedFalse(
                payment.getRegistration().getId(), com.modeltech.datamasteryhub.modules.training.entity.Invoice.ISSUED).isPresent());
        return response;
    }

    private RegistrationResponse toResponse(Registration reg) {
        RegistrationResponse response = registrationMapper.toResponse(reg);
        response.setPaymentSummary(paymentMapper.summarize(
                paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(reg.getId())));
        return response;
    }

    private PaymentNotice notice(Registration reg, Payment p, String reason) {
        return new PaymentNotice(reg.getEmail(), reg.getFirstName(), reg.getLastName(),
                reg.getBootcampTitle(), reg.getSessionName(), p.getAmount(), p.getCurrency(),
                p.getInstallmentNumber(), p.getInstallmentCount(), p.getDueDate(),
                paymentMapper.paymentLink(p),
                p.getMethod() != null ? p.getMethod().name() : null, p.getReference(), reason);
    }

    private void deleteProofQuietly(Payment payment) {
        String key = payment.getProofObjectKey();
        payment.setProofObjectKey(null);
        payment.setProofUrl(null);
        if (key == null || key.isBlank()) return;
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Suppression du justificatif impossible ({}): {}", key, e.getMessage());
        }
    }

    private Registration getRegistration(UUID id) {
        return registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));
    }

    private Payment getPayment(UUID id) {
        return paymentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement", "id", id));
    }

    private Payment getPaymentByToken(String token) {
        Payment payment = paymentRepository.findByPublicTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("Lien de paiement invalide."));
        if (payment.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Ce lien de paiement a expiré. Contactez Model Technologie pour en recevoir un nouveau.");
        }
        return payment;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred.trim() : fallback;
    }
}
