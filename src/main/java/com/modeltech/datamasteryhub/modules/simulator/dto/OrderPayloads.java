package com.modeltech.datamasteryhub.modules.simulator.dto;

import com.modeltech.datamasteryhub.modules.simulator.entity.ManualMethod;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderChannel;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Forfaits, commandes en ligne et état de la passerelle. Les montants sont en francs CFA (XOF, sans décimale). */
public final class OrderPayloads {

    private OrderPayloads() {}

    // ── Public ───────────────────────────────────────────────────────

    /** @param onlineFeeXof frais répercutés sur un paiement en ligne (0 si absorbés) ; @param onlineTotalXof montant encaissé en ligne (prix + frais) */
    public record PlanView(PlanCode code, String name, int priceXof, int durationDays, int examAttempts, int onlineFeeXof, int onlineTotalXof) {}

    /** @param paymentsEnabled faux tant que la passerelle n'est pas configurée : la page n'affiche alors pas de bouton « Acheter » */
    public record PlansView(boolean online, boolean manual, boolean fallback, String fallbackReason, List<PlanView> plans) {}

    public record PurchaseRequest(
            @NotNull PlanCode plan,
            /** Absent : le serveur choisit (en ligne si proposé, sinon manuel). */
            OrderChannel channel,
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 255) String email,
            @Size(max = 50) String phone,
            @AssertTrue(message = "Vous devez accepter les conditions d'utilisation et de vente") boolean acceptTerms,
            /** Textes légaux affichés au moment de l'acceptation (identifiants des versions en vigueur) : le serveur vérifie qu'ils sont à jour. */
            List<UUID> acceptedDocuments) {}

    /** {@code checkoutUrl} est vide pour un paiement manuel : l'acheteur est dirigé vers la page de suivi, qui affiche les consignes. */
    public record PurchaseResponse(UUID orderRef, String checkoutUrl, OrderChannel channel) {}

    public record DeclareRequest(@NotNull ManualMethod method, @NotBlank @Size(max = 120) String reference) {}

    public record RejectOrderRequest(@NotBlank @Size(max = 500) String reason) {}

    public record ProofLink(String url) {}

    /** Ce qu'une personne sans compte peut savoir de sa commande à partir de sa référence opaque. */
    public record OrderStatusView(UUID ref, OrderStatus status, PlanCode plan, String planName, int amountXof, int chargedXof, int feeXof, OrderChannel channel,
                                  String emailHint, String receiptUrl, ManualMethod declaredMethod, boolean proofUploaded, String note) {}

    // ── Administration ───────────────────────────────────────────────

    public record AdminPlanView(PlanCode code, String name, int priceXof, int durationDays, int examAttempts, boolean active, LocalDateTime updatedAt, String updatedBy) {}

    public record PlanUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(100) @Max(5_000_000) int priceXof,
            @Min(1) @Max(365) int durationDays,
            @Min(0) @Max(50) int examAttempts,
            boolean active) {}

    public record OrderView(UUID id, UUID ref, String learnerEmail, String learnerName, PlanCode plan, String planName, int amountXof, int feeXof, int chargedXof,
                            OrderChannel channel, OrderStatus status, String provider, String providerMode, String providerToken, String failureReason,
                            ManualMethod manualMethod, String manualReference, LocalDateTime declaredAt, boolean proofUploaded, String confirmedBy,
                            UUID grantId, LocalDateTime createdAt, LocalDateTime paidAt) {}

    /** État de la passerelle pour l'administration : jamais de clé, seulement ce qui permet de vérifier la mise en place. */
    public record GatewayStatus(String provider, boolean configured, String mode, String notificationUrl) {}
}
