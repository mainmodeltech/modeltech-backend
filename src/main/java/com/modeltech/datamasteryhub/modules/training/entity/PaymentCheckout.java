package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.modules.training.enums.CheckoutScope;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tentative de paiement en ligne d'une inscription : une échéance ou tout le solde. Le montant encaissé (prix + frais éventuels) est
 * figé ici ; les échéances gardent le prix du catalogue. Elle ne confirme les échéances qu'après relecture du paiement chez le prestataire.
 */
@Entity
@Table(name = "payment_checkout")
@Getter @Setter @NoArgsConstructor
public class PaymentCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Référence opaque (122 bits) de l'adresse de retour : elle seule permet de suivre la tentative sans compte. */
    @Column(name = "public_ref", nullable = false, unique = true, updatable = false)
    private UUID publicRef = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CheckoutScope scope;

    /** Somme des échéances couvertes (prix du catalogue). */
    @Column(name = "amount_xof", nullable = false)
    private long amountXof;

    @Column(name = "fee_xof", nullable = false)
    private long feeXof;

    /** Montant réellement encaissé : c'est lui que le prestataire doit confirmer. */
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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "payment_checkout_item", joinColumns = @JoinColumn(name = "checkout_id"), inverseJoinColumns = @JoinColumn(name = "payment_id"))
    private Set<Payment> payments = new LinkedHashSet<>();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
