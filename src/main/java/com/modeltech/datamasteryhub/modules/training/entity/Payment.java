package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une échéance de paiement d'une inscription (1 à N par inscription).
 * Aucune donnée d'identité ici : elle reste sur {@link Registration}.
 */
@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency = "XOF";

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber = 1;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount = 1;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod method;

    private String reference;

    @Column(name = "declared_at")
    private LocalDateTime declaredAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "proof_object_key", length = 512)
    private String proofObjectKey;

    @Column(name = "proof_url", length = 1024)
    private String proofUrl;

    /** Jeton secret du lien de paiement public (256 bits). */
    @Column(name = "public_token", nullable = false, updatable = false)
    private String publicToken;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column(name = "reminder_count", nullable = false)
    private Integer reminderCount = 0;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    @Column(name = "invoice_ref")
    private String invoiceRef;

    @Column(name = "purchase_order_ref")
    private String purchaseOrderRef;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refunded_by")
    private String refundedBy;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;
}
