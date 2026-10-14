package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminPaymentResponse {
    private UUID id;
    private UUID registrationId;
    private String firstName;
    private String lastName;
    private String email;
    private String bootcampTitle;
    private String sessionName;
    private Long amount;
    private String currency;
    private Integer installmentNumber;
    private Integer installmentCount;
    private LocalDate dueDate;
    private PaymentStatus status;
    private PaymentMethod method;
    private String reference;
    private LocalDateTime declaredAt;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private String rejectionReason;
    private String proofUrl;
    private String invoiceRef;
    private String purchaseOrderRef;
    private String notes;
    private LocalDateTime refundedAt;
    private String refundReason;
    private String recordedBy;
    private boolean confirmedUnchecked;
    /** Demande de remboursement en attente de validation (renseignée tant qu'elle n'est ni validée ni refusée). */
    private LocalDateTime refundRequestedAt;
    private String refundRequestedBy;
    private String refundRequestReason;
    private boolean refundUnchecked;
    private Integer reminderCount;
    private LocalDateTime lastReminderAt;
    /** Lien de paiement à communiquer au candidat. */
    private String paymentLink;
    private LocalDateTime createdAt;
}
