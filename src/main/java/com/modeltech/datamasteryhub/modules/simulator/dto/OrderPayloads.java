package com.modeltech.datamasteryhub.modules.simulator.dto;

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

    public record PlanView(PlanCode code, String name, int priceXof, int durationDays, int examAttempts) {}

    /** @param paymentsEnabled faux tant que la passerelle n'est pas configurée : la page n'affiche alors pas de bouton « Acheter » */
    public record PlansView(boolean paymentsEnabled, List<PlanView> plans) {}

    public record PurchaseRequest(
            @NotNull PlanCode plan,
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 255) String email,
            @Size(max = 50) String phone,
            @AssertTrue(message = "Vous devez accepter les conditions d'utilisation et de vente") boolean acceptTerms) {}

    public record PurchaseResponse(UUID orderRef, String checkoutUrl) {}

    /** Ce qu'une personne sans compte peut savoir de sa commande à partir de sa référence opaque. */
    public record OrderStatusView(UUID ref, OrderStatus status, PlanCode plan, String planName, int amountXof, String emailHint, String receiptUrl) {}

    // ── Administration ───────────────────────────────────────────────

    public record AdminPlanView(PlanCode code, String name, int priceXof, int durationDays, int examAttempts, boolean active, LocalDateTime updatedAt, String updatedBy) {}

    public record PlanUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(100) @Max(5_000_000) int priceXof,
            @Min(1) @Max(365) int durationDays,
            @Min(0) @Max(50) int examAttempts,
            boolean active) {}

    public record OrderView(UUID id, UUID ref, String learnerEmail, String learnerName, PlanCode plan, String planName, int amountXof,
                            OrderStatus status, String provider, String providerMode, String providerToken, String failureReason,
                            UUID grantId, LocalDateTime createdAt, LocalDateTime paidAt) {}

    /** État de la passerelle pour l'administration : jamais de clé, seulement ce qui permet de vérifier la mise en place. */
    public record GatewayStatus(String provider, boolean configured, String mode, String notificationUrl) {}
}
