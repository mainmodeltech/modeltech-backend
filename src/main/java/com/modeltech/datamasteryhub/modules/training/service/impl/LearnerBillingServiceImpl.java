package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.training.dto.LearnerBillingPayloads;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.mapper.PaymentMapper;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.service.LearnerBillingService;
import com.modeltech.datamasteryhub.modules.training.service.LearnerDocumentPdfGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearnerBillingServiceImpl implements LearnerBillingService {

    private final LearnerRepository learnerRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentMapper paymentMapper;
    private final LearnerDocumentPdfGenerator pdf;

    @Value("${app.timezone:Africa/Dakar}")
    private String timezone;

    @Override
    public List<LearnerBillingPayloads.PaymentItem> payments(String learnerEmail) {
        Learner learner = requireLearner(learnerEmail);
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        return paymentRepository.findAllByLearner(learner.getId()).stream().map(p -> {
            Registration r = p.getRegistration();
            boolean payable = (p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.DECLARED)
                    && p.getPublicToken() != null && p.getTokenExpiresAt() != null && p.getTokenExpiresAt().isAfter(now);
            return LearnerBillingPayloads.PaymentItem.builder()
                    .id(p.getId().toString())
                    .registrationId(r.getId().toString())
                    .formationTitle(r.getBootcampTitle())
                    .sessionName(r.getSessionName())
                    .installmentNumber(p.getInstallmentNumber())
                    .installmentCount(p.getInstallmentCount())
                    .amount(p.getAmount())
                    .currency(p.getCurrency())
                    .totalAmount(r.getTotalAmount())
                    .dueDate(p.getDueDate())
                    .status(p.getStatus().name())
                    .method(p.getMethod() != null ? p.getMethod().name() : null)
                    .confirmedAt(p.getConfirmedAt())
                    .payUrl(payable ? paymentMapper.paymentLink(p) : null)
                    .receiptUrl(p.getStatus() == PaymentStatus.CONFIRMED ? "/api/v1/learner/payments/" + p.getId() + "/receipt" : null)
                    .build();
        }).toList();
    }

    @Override
    public List<LearnerBillingPayloads.EnrollmentItem> enrollments(String learnerEmail) {
        Learner learner = requireLearner(learnerEmail);
        Map<UUID, Enrollment> byFormation = new LinkedHashMap<>();
        enrollmentRepository.findAccessibleByLearner(learner.getId())
                .forEach(e -> byFormation.putIfAbsent(e.getRegistration().getBootcamp().getId(), e));
        return byFormation.values().stream().map(e -> {
            UUID formationId = e.getRegistration().getBootcamp().getId();
            return LearnerBillingPayloads.EnrollmentItem.builder()
                    .formationId(formationId.toString())
                    .title(e.getRegistration().getBootcamp().getTitle())
                    .sessionName(e.getRegistration().getSessionName())
                    .status(e.getStatus().name())
                    .accessStartsAt(e.getAccessStartsAt())
                    .accessEndsAt(e.getAccessEndsAt())
                    .trainerName(trainerOf(e))
                    .attestationUrl("/api/v1/learner/enrollments/" + formationId + "/attestation")
                    .build();
        }).toList();
    }

    @Override
    public byte[] receipt(String learnerEmail, UUID paymentId) {
        Learner learner = requireLearner(learnerEmail);
        Payment payment = paymentRepository.findByIdAndIsDeletedFalse(paymentId)
                .filter(p -> p.getRegistration().getLearner() != null
                        && p.getRegistration().getLearner().getId().equals(learner.getId()))
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                .orElseThrow(() -> new ResourceNotFoundException("Reçu", "id", paymentId));
        return buildReceipt(payment);
    }

    @Override
    public byte[] receiptByToken(String paymentToken) {
        Payment payment = paymentRepository.findByPublicTokenAndIsDeletedFalse(paymentToken)
                .filter(p -> p.getTokenExpiresAt() != null && p.getTokenExpiresAt().isAfter(LocalDateTime.now()))
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun reçu pour ce lien."));
        return buildReceipt(payment);
    }

    @Override
    public byte[] attestation(String learnerEmail, UUID formationId) {
        Learner learner = requireLearner(learnerEmail);
        Enrollment e = enrollmentRepository.findAccessibleByLearner(learner.getId()).stream()
                .filter(x -> x.getRegistration().getBootcamp().getId().equals(formationId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "formation", formationId));
        return pdf.attestation(new LearnerDocumentPdfGenerator.Attestation(
                learner.getFullName(),
                e.getRegistration().getBootcamp().getTitle(),
                e.getRegistration().getBootcamp().getDuration(),
                e.getRegistration().getSessionName(),
                e.getAccessStartsAt(), e.getAccessEndsAt(),
                trainerOf(e),
                LocalDate.now(ZoneId.of(timezone))));
    }

    // ── Privé ───────────────────────────────────────────────────────

    private byte[] buildReceipt(Payment p) {
        Registration r = p.getRegistration();
        List<Payment> all = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(r.getId());
        long paidSoFar = all.stream().filter(x -> x.getStatus() == PaymentStatus.CONFIRMED).mapToLong(Payment::getAmount).sum();
        LocalDate date = (p.getConfirmedAt() != null ? p.getConfirmedAt() : LocalDateTime.now()).toLocalDate();
        long total = r.getTotalAmount() != null ? r.getTotalAmount()
                : all.stream().filter(x -> x.getStatus() != PaymentStatus.CANCELLED).mapToLong(Payment::getAmount).sum();
        return pdf.receipt(new LearnerDocumentPdfGenerator.Receipt(
                "REC-" + date.getYear() + "-" + p.getId().toString().substring(0, 8).toUpperCase(),
                date,
                (r.getFirstName() + " " + (r.getLastName() == null ? "" : r.getLastName())).trim(),
                r.getEmail(),
                r.getBootcampTitle(),
                r.getSessionName(),
                p.getInstallmentNumber(), p.getInstallmentCount(),
                p.getAmount(), p.getCurrency(),
                methodLabel(p.getMethod()), p.getReference(),
                total, paidSoFar));
    }

    private Learner requireLearner(String email) {
        return learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Espace réservé aux apprenants."));
    }

    private static String trainerOf(Enrollment e) {
        return e.getSession() != null && e.getSession().getTrainer() != null ? e.getSession().getTrainer().getFullName() : null;
    }

    private static String methodLabel(PaymentMethod m) {
        if (m == null) return null;
        return switch (m) {
            case WAVE -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
            case VIREMENT -> "Virement bancaire";
            case ENTREPRISE -> "Paiement par l'entreprise";
            case ESPECES -> "Espèces";
        };
    }
}
