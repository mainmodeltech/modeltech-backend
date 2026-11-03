package com.modeltech.datamasteryhub.modules.coaching.entity;

import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tentative de paiement en ligne d'une demande de coaching : montant figé (prix + frais éventuels), confirmée seulement après relecture chez le prestataire. */
@Entity
@Table(name = "coaching_checkout")
@Getter @Setter @NoArgsConstructor
public class CoachingCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Référence opaque (122 bits) de l'adresse de retour. */
    @Column(name = "public_ref", nullable = false, unique = true, updatable = false)
    private UUID publicRef = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coaching_request_id", nullable = false)
    private CoachingRequest request;

    @Column(name = "amount_xof", nullable = false)
    private long amountXof;

    @Column(name = "fee_xof", nullable = false)
    private long feeXof;

    @Column(name = "charged_xof", nullable = false)
    private long chargedXof;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CheckoutStatus status = CheckoutStatus.PENDING;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_mode", length = 10)
    private String providerMode;

    @Column(name = "provider_token")
    private String providerToken;

    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Column(name = "receipt_url", length = 1024)
    private String receiptUrl;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
