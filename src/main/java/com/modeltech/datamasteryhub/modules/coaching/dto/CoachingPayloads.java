package com.modeltech.datamasteryhub.modules.coaching.dto;

import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.ManualMethod;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderChannel;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Demandes de coaching et leur paiement. Les montants sont en francs CFA (XOF, sans décimale). */
public final class CoachingPayloads {

    private CoachingPayloads() {}

    // ── Public : demande ─────────────────────────────────────────────

    public record RequestForm(
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 255) String email,
            @Size(max = 50) String phone,
            @Size(max = 255) String company,
            @Size(max = 160) String tier,
            @Size(max = 200) String slot,
            @Size(max = 4000) String message,
            @AssertTrue(message = "Vous devez accepter les conditions pour envoyer votre demande") boolean acceptTerms) {}

    /** @param bookingUrl lien de réservation d'un échange de présentation (15 à 20 minutes), absent s'il n'est pas configuré */
    public record RequestReceipt(String message, String bookingUrl) {}

    // ── Public : page de paiement (lien personnel) ───────────────────

    /** @param onlineFeeXof frais répercutés sur un paiement en ligne (0 si absorbés) ; @param onlineTotalXof montant encaissé en ligne */
    public record PayOptions(boolean online, boolean manual, boolean fallback, String reason, long onlineFeeXof, long onlineTotalXof) {}

    /** Ce que voit le détenteur du lien : jamais d'e-mail, de téléphone ni de note interne. */
    public record PayView(String firstName, String offerLabel, String trainerName, CoachingStatus status, long amountXof, PayOptions options,
                          ManualMethod declaredMethod, boolean proofUploaded, String rejectNote, OrderChannel paidChannel, String receiptUrl,
                          String bookingUrl, LocalDateTime expiresAt) {}

    public record StartResponse(UUID checkoutRef, String checkoutUrl) {}

    public record CheckoutView(UUID ref, CheckoutStatus status, long chargedXof, String receiptUrl) {}

    // ── Administration ───────────────────────────────────────────────

    public record RequestView(UUID id, String firstName, String lastName, String email, String phone, String company, String tier, String slot,
                              String message, LocalDateTime termsAcceptedAt, CoachingStatus status, String assignedTo, String trainerEmail, String trainerName,
                              String internalNotes, String closingReason, String offerLabel, Long amountXof, String validatedBy, LocalDateTime validatedAt,
                              String paymentLink, LocalDateTime tokenExpiresAt, ManualMethod manualMethod, String manualReference, LocalDateTime declaredAt,
                              boolean proofUploaded, String rejectNote, String confirmedBy, LocalDateTime confirmedAt, LocalDateTime paidAt,
                              Long paidAmountXof, OrderChannel paidChannel, LocalDateTime createdAt) {}

    /** Nombre de demandes par statut (onglets de la page d'administration). */
    public record Summary(Map<CoachingStatus, Long> counts) {}

    public record ReviewUpdate(@Size(max = 255) String trainerEmail, @Size(max = 255) String trainerName, @Size(max = 4000) String internalNotes) {}

    /** Validation : l'équipe a analysé la demande et confirmé la disponibilité du formateur ; fixe l'offre et le montant. */
    public record ValidateRequest(
            @NotBlank @Size(max = 255) String offerLabel,
            @Min(100) @Max(10_000_000) long amountXof,
            @NotBlank @Size(max = 255) String trainerName,
            @Size(max = 255) String trainerEmail,
            @Size(max = 4000) String internalNotes,
            @AssertTrue(message = "Confirmez d'abord la disponibilité avec le formateur") boolean trainerConfirmed) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record ProofLink(String url) {}
}
