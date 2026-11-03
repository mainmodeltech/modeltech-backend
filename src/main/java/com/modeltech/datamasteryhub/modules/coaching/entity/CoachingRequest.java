package com.modeltech.datamasteryhub.modules.coaching.entity;

import com.modeltech.datamasteryhub.modules.simulator.entity.ManualMethod;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Demande de coaching. Le visiteur décrit son besoin ; l'équipe l'analyse, confirme le formateur, puis valide : l'offre et le montant sont
 * fixés à ce moment-là par une personne de l'équipe, et le lien de paiement personnel est envoyé par e-mail.
 */
@Entity
@Table(name = "coaching_request")
@Getter @Setter @NoArgsConstructor
public class CoachingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Secret (256 bits) du lien de paiement ; absent tant que la demande n'est pas validée. */
    @Column(name = "pay_token", unique = true, length = 64)
    private String payToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(length = 50)
    private String phone;

    private String company;

    /** Formule choisie sur la page publique (texte libre : l'équipe fixe l'offre réelle à la validation). */
    @Column(length = 160)
    private String tier;

    /** Créneau souhaité (texte libre). */
    @Column(length = 200)
    private String slot;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "terms_accepted_at", nullable = false)
    private LocalDateTime termsAcceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoachingStatus status = CoachingStatus.NEW;

    /** Personne de l'équipe qui traite la demande. */
    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "trainer_email")
    private String trainerEmail;

    @Column(name = "trainer_name")
    private String trainerName;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    /** Motif d'un refus ou d'une annulation (montré au client pour un refus). */
    @Column(name = "closing_reason", columnDefinition = "TEXT")
    private String closingReason;

    @Column(name = "offer_label")
    private String offerLabel;

    /** Prix convenu, en francs CFA. Fixé à la validation. */
    @Column(name = "amount_xof")
    private Long amountXof;

    @Column(name = "validated_by")
    private String validatedBy;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "manual_method", length = 20)
    private ManualMethod manualMethod;

    @Column(name = "manual_reference", length = 120)
    private String manualReference;

    @Column(name = "declared_at")
    private LocalDateTime declaredAt;

    @Column(name = "proof_object_key", length = 512)
    private String proofObjectKey;

    /** Motif du dernier refus d'un paiement déclaré (montré au client pour qu'il corrige). */
    @Column(name = "reject_note", columnDefinition = "TEXT")
    private String rejectNote;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "paid_amount_xof")
    private Long paidAmountXof;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_channel", length = 10)
    private OrderChannel paidChannel;

    @Column(name = "receipt_url", length = 1024)
    private String receiptUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public String fullName() {
        return (firstName + " " + (lastName == null ? "" : lastName)).trim();
    }
}
