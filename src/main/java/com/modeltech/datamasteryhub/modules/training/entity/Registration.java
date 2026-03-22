package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
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

    // ── Paiement ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 50)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** Montant du en FCFA (prix - reduction promo) */
    @Column(name = "amount_due")
    private Integer amountDue;

    /** Montant total paye (denormalise, somme des paiements) */
    @Column(name = "amount_paid", nullable = false)
    private Integer amountPaid = 0;

    @OneToMany(mappedBy = "registration", fetch = FetchType.LAZY)
    @OrderBy("paymentDate ASC")
    private List<Payment> payments = new ArrayList<>();
}
