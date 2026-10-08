package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.training.enums.PayerType;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Inscription d'un visiteur a un bootcamp.
 */
@Entity
@Table(name = "registrations")
@Getter @Setter @NoArgsConstructor
public class Registration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bootcamp_id")
    private Bootcamp bootcamp;

    @Column(name = "bootcamp_title")
    private String bootcampTitle;

    // Session liee
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bootcamp_session_id")
    private BootcampSession session;

    @Column(name = "session_name")
    private String sessionName;

    // Code promo utilise
    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    @Column(name = "promo_code_used", length = 50)
    private String promoCodeUsed;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;
    /** Pays de provenance */
    private String country;

    /**
     * Profil de l'inscrit.
     * Stocké en TEXT uppercase (compatible CHECK constraint PostgreSQL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "profile")
    private RegistrationProfile profile;

    /**
     * École / institution — renseignée uniquement pour les étudiants.
     */
    private String school;

    // ── Champs professionnels (existants) ─────────────────────────────────────

    /** Organisation — obligatoire pour PROFESSIONAL, optionnel pour ENTREPRENEUR */
    private String company;

    /** Poste actuel / secteur — obligatoire pour PROFESSIONAL, optionnel pour ENTREPRENEUR */
    private String position;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    // ── Acceptation et paiement (V21) ────────────────────────────────────────

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_by")
    private String acceptedBy;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", length = 20)
    private PayerType payerType;

    /** Montant total dû (XOF), figé à l'acceptation : prix, early-bird et remise promo appliqués. */
    @Column(name = "total_amount")
    private Long totalAmount;

    /** WEBSITE (formulaire public) ou ADMIN (inscription manuelle). */
    @Column(nullable = false, length = 10)
    private String source = "WEBSITE";

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    /** Compte apprenant rattaché quand le premier paiement est confirmé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id")
    private Learner learner;
}
