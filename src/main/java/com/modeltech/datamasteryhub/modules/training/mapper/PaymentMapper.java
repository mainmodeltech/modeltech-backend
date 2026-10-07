package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.EnrollmentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.PaymentSummaryResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.PublicPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Conversions manuelles des paiements (liens, agrégats : rien que MapStruct ne sache exprimer simplement). */
@Component
public class PaymentMapper {

    private static final List<PaymentMethod> PUBLIC_METHODS =
            List.of(PaymentMethod.WAVE, PaymentMethod.ORANGE_MONEY, PaymentMethod.VIREMENT);

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.frontend.payment-path:/paiement}")
    private String paymentPath;

    @Value("${app.notifications.phone.to:788625238}")
    private String paymentPhone;

    public String paymentLink(Payment payment) {
        return frontendUrl + paymentPath + "/" + payment.getPublicToken();
    }

    public boolean isPublicMethod(PaymentMethod method) {
        return PUBLIC_METHODS.contains(method);
    }

    public AdminPaymentResponse toAdmin(Payment p) {
        Registration r = p.getRegistration();
        return AdminPaymentResponse.builder()
                .id(p.getId())
                .registrationId(r.getId())
                .firstName(r.getFirstName())
                .lastName(r.getLastName())
                .email(r.getEmail())
                .bootcampTitle(r.getBootcampTitle())
                .sessionName(r.getSessionName())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .installmentNumber(p.getInstallmentNumber())
                .installmentCount(p.getInstallmentCount())
                .dueDate(p.getDueDate())
                .status(p.getStatus())
                .method(p.getMethod())
                .reference(p.getReference())
                .declaredAt(p.getDeclaredAt())
                .paidAt(p.getPaidAt())
                .confirmedAt(p.getConfirmedAt())
                .confirmedBy(p.getConfirmedBy())
                .rejectionReason(p.getRejectionReason())
                .proofUrl(p.getProofUrl())
                .invoiceRef(p.getInvoiceRef())
                .purchaseOrderRef(p.getPurchaseOrderRef())
                .notes(p.getNotes())
                .reminderCount(p.getReminderCount())
                .lastReminderAt(p.getLastReminderAt())
                .paymentLink(paymentLink(p))
                .createdAt(p.getCreatedAt())
                .build();
    }

    /** Page publique : uniquement ce que le détenteur du lien doit voir (pas d'e-mail, de téléphone, de jeton des autres échéances). */
    public PublicPaymentResponse toPublic(Payment p, List<Payment> allPayments) {
        Registration r = p.getRegistration();
        List<PublicPaymentResponse.ScheduleItem> schedule = allPayments.stream()
                .filter(x -> x.getStatus() != PaymentStatus.CANCELLED)
                .sorted(Comparator.comparing(Payment::getInstallmentNumber))
                .map(x -> PublicPaymentResponse.ScheduleItem.builder()
                        .installmentNumber(x.getInstallmentNumber())
                        .amount(x.getAmount())
                        .dueDate(x.getDueDate())
                        .status(x.getStatus())
                        .build())
                .toList();
        return PublicPaymentResponse.builder()
                .firstName(r.getFirstName())
                .bootcampTitle(r.getBootcampTitle())
                .sessionName(r.getSessionName())
                .sessionStartDate(r.getSession() != null ? r.getSession().getStartDate() : null)
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .installmentNumber(p.getInstallmentNumber())
                .installmentCount(p.getInstallmentCount())
                .dueDate(p.getDueDate())
                .status(p.getStatus())
                .method(p.getMethod())
                .reference(p.getReference())
                .hasProof(p.getProofObjectKey() != null)
                .rejectionReason(p.getRejectionReason())
                .totalAmount(r.getTotalAmount())
                .schedule(schedule)
                .payTo(PublicPaymentResponse.PayTo.builder().phone(paymentPhone).methods(PUBLIC_METHODS).build())
                .build();
    }

    /** Agrégat pour les cartes du Kanban ; null si l'inscription n'a (encore) aucune échéance. */
    public PaymentSummaryResponse summarize(List<Payment> payments) {
        List<Payment> active = payments.stream().filter(p -> p.getStatus() != PaymentStatus.CANCELLED).toList();
        if (active.isEmpty()) return null;

        long paid = active.stream().filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                .mapToLong(Payment::getAmount).sum();
        LocalDate nextDue = active.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.DECLARED)
                .map(Payment::getDueDate).filter(d -> d != null)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime linkSentAt = active.stream().map(Payment::getCreatedAt).filter(d -> d != null)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime lastReminder = active.stream().map(Payment::getLastReminderAt).filter(d -> d != null)
                .max(Comparator.naturalOrder()).orElse(null);
        Payment declared = active.stream().filter(p -> p.getStatus() == PaymentStatus.DECLARED)
                .min(Comparator.comparing(Payment::getInstallmentNumber)).orElse(null);

        return PaymentSummaryResponse.builder()
                .installmentCount(active.size())
                .confirmedCount((int) active.stream().filter(p -> p.getStatus() == PaymentStatus.CONFIRMED).count())
                .paidAmount(paid)
                .nextDueDate(nextDue)
                .linkSentAt(linkSentAt)
                .lastReminderAt(lastReminder)
                .declaredMethod(declared != null && declared.getMethod() != null ? declared.getMethod().name() : null)
                .declaredReference(declared != null ? declared.getReference() : null)
                .declaredHasProof(declared != null ? declared.getProofObjectKey() != null : null)
                .build();
    }

    public EnrollmentResponse toEnrollment(Enrollment e) {
        return EnrollmentResponse.builder()
                .id(e.getId())
                .learnerId(e.getLearner().getId())
                .learnerEmail(e.getLearner().getEmail())
                .learnerName(e.getLearner().getFullName())
                .registrationId(e.getRegistration().getId())
                .sessionId(e.getSession() != null ? e.getSession().getId() : null)
                .sessionName(e.getRegistration().getSessionName())
                .bootcampTitle(e.getRegistration().getBootcampTitle())
                .status(e.getStatus())
                .accessStartsAt(e.getAccessStartsAt())
                .accessEndsAt(e.getAccessEndsAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
