package com.modeltech.datamasteryhub.modules.simulator.entity;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Commande en ligne d'un forfait. Montant, durée et quota sont figés à la commande (un changement de prix ultérieur ne la touche pas). */
@Entity
@Table(name = "sim_order")
@Getter @Setter @NoArgsConstructor
public class SimOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Référence opaque (122 bits aléatoires) utilisée dans l'adresse de retour : elle seule permet de suivre une commande sans compte. */
    @Column(name = "public_ref", nullable = false, unique = true, updatable = false)
    private UUID publicRef = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 20)
    private PlanCode planCode;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "amount_xof", nullable = false)
    private int amountXof;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "exam_attempts", nullable = false)
    private int examAttempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_mode", length = 10)
    private String providerMode;

    @Column(name = "provider_token", unique = true)
    private String providerToken;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "receipt_url", columnDefinition = "TEXT")
    private String receiptUrl;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "grant_id")
    private UUID grantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Version
    private int version;
}
