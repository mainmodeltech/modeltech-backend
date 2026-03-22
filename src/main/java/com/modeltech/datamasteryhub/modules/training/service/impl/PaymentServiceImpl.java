package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.SessionFinancialSummaryResponse;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.mapper.PaymentMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository        paymentRepository;
    private final RegistrationRepository   registrationRepository;
    private final BootcampSessionRepository sessionRepository;
    private final PaymentMapper            paymentMapper;

    // =========================================================================
    //  CRUD PAIEMENTS
    // =========================================================================

    @Override
    @Transactional
    public PaymentResponse addPayment(UUID registrationId, CreatePaymentRequest request) {
        Registration registration = findRegistrationOrThrow(registrationId);

        Payment payment = paymentMapper.toEntity(request);
        payment.setRegistration(registration);
        payment.setRecordedBy(getCurrentUsername());

        Payment saved = paymentRepository.save(payment);

        recalculatePaymentStatus(registration);

        log.info("Paiement enregistre : {} FCFA via {} sur inscription {} (ref: {})",
                saved.getAmount(), saved.getPaymentMethod(),
                registrationId, saved.getReference());

        return paymentMapper.toResponse(saved);
    }

    @Override
    public List<PaymentResponse> findByRegistration(UUID registrationId) {
        // Verifie que l'inscription existe
        findRegistrationOrThrow(registrationId);
        return paymentMapper.toResponseList(
                paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByPaymentDateAsc(registrationId));
    }

    @Override
    @Transactional
    public void deletePayment(UUID paymentId) {
        Payment payment = paymentRepository.findByIdAndIsDeletedFalse(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement", "id", paymentId));

        payment.setDeleted(true);
        payment.setDeletedAt(LocalDateTime.now());
        payment.setDeletedBy(getCurrentUsername());
        paymentRepository.save(payment);

        recalculatePaymentStatus(payment.getRegistration());

        log.info("Paiement {} supprime (inscription {})", paymentId, payment.getRegistration().getId());
    }

    // =========================================================================
    //  RESUME FINANCIER PAR SESSION
    // =========================================================================

    @Override
    public SessionFinancialSummaryResponse getFinancialSummary(UUID sessionId) {
        BootcampSession session = sessionRepository.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));

        // Inscriptions actives (non supprimees, non annulees)
        List<Registration> registrations = registrationRepository
                .findAllBySessionIdAndIsDeletedFalse(sessionId)
                .stream()
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
                .toList();

        int revenueTarget = registrations.stream()
                .mapToInt(r -> r.getAmountDue() != null ? r.getAmountDue() : 0)
                .sum();

        int totalCollected = registrations.stream()
                .mapToInt(r -> r.getAmountPaid() != null ? r.getAmountPaid() : 0)
                .sum();

        int remaining = Math.max(0, revenueTarget - totalCollected);
        double collectionRate = revenueTarget > 0
                ? Math.round((double) totalCollected / revenueTarget * 10000.0) / 100.0
                : 0.0;

        long paidCount    = registrations.stream().filter(r -> r.getPaymentStatus() == PaymentStatus.PAID).count();
        long partialCount = registrations.stream().filter(r -> r.getPaymentStatus() == PaymentStatus.PARTIAL).count();
        long unpaidCount  = registrations.stream().filter(r -> r.getPaymentStatus() == PaymentStatus.UNPAID).count();

        return SessionFinancialSummaryResponse.builder()
                .sessionId(sessionId)
                .sessionName(session.getSessionName())
                .totalRegistrations(registrations.size())
                .revenueTarget(revenueTarget)
                .totalCollected(totalCollected)
                .remainingToCollect(remaining)
                .collectionRate(collectionRate)
                .paidCount((int) paidCount)
                .partialCount((int) partialCount)
                .unpaidCount((int) unpaidCount)
                .build();
    }

    // =========================================================================
    //  CALCUL DU MONTANT DU
    // =========================================================================

    /**
     * Calcule le montant du pour une inscription a partir du prix de la session/bootcamp
     * et de la reduction promo code eventuelle.
     */
    public static int calculateAmountDue(Registration registration) {
        int basePrice = resolveBasePrice(registration);

        if (registration.getDiscountPercent() != null && registration.getDiscountPercent() > 0) {
            int discount = basePrice * registration.getDiscountPercent() / 100;
            return basePrice - discount;
        }
        return basePrice;
    }

    // =========================================================================
    //  METHODES PRIVEES
    // =========================================================================

    /**
     * Recalcule le statut de paiement d'une inscription apres ajout/suppression
     * d'un paiement. Met a jour amountPaid et paymentStatus.
     */
    private void recalculatePaymentStatus(Registration registration) {
        int totalPaid = paymentRepository.sumAmountByRegistrationId(registration.getId());
        registration.setAmountPaid(totalPaid);

        int amountDue = registration.getAmountDue() != null ? registration.getAmountDue() : 0;

        PaymentStatus newStatus;
        if (totalPaid <= 0) {
            newStatus = PaymentStatus.UNPAID;
        } else if (totalPaid >= amountDue && amountDue > 0) {
            newStatus = PaymentStatus.PAID;
        } else {
            newStatus = PaymentStatus.PARTIAL;
        }

        registration.setPaymentStatus(newStatus);
        registrationRepository.save(registration);

        log.info("Inscription {} : paiement {} / {} FCFA → statut {}",
                registration.getId(), totalPaid, amountDue, newStatus);
    }

    /**
     * Resout le prix de base a partir de la session (priceOverride / earlyBirdPrice)
     * ou du bootcamp (price). Les prix sont stockes en String ("450 000 FCFA"),
     * on parse en entier.
     */
    private static int resolveBasePrice(Registration registration) {
        BootcampSession session = registration.getSession();

        if (session != null) {
            // Early bird si applicable
            if (session.getEarlyBirdPrice() != null && session.getEarlyBirdDeadline() != null
                    && !LocalDate.now().isAfter(session.getEarlyBirdDeadline())) {
                int earlyBird = parsePriceFcfa(session.getEarlyBirdPrice());
                if (earlyBird > 0) return earlyBird;
            }

            // Prix session override
            if (session.getPriceOverride() != null) {
                int override = parsePriceFcfa(session.getPriceOverride());
                if (override > 0) return override;
            }

            // Prix du bootcamp parent
            if (session.getBootcamp() != null && session.getBootcamp().getPrice() != null) {
                int bootcampPrice = parsePriceFcfa(session.getBootcamp().getPrice());
                if (bootcampPrice > 0) return bootcampPrice;
            }
        }

        // Bootcamp directement lie
        if (registration.getBootcamp() != null && registration.getBootcamp().getPrice() != null) {
            return parsePriceFcfa(registration.getBootcamp().getPrice());
        }

        return 0;
    }

    /**
     * Parse un prix affiche ("450 000 FCFA", "150000", "300 000") en entier.
     * Supprime tout ce qui n'est pas un chiffre.
     */
    static int parsePriceFcfa(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) return 0;
        String digits = priceStr.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Registration findRegistrationOrThrow(UUID registrationId) {
        return registrationRepository.findByIdAndIsDeletedFalse(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", registrationId));
    }

    private String getCurrentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
