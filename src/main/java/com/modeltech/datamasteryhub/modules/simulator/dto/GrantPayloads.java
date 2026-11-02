package com.modeltech.datamasteryhub.modules.simulator.dto;

import com.modeltech.datamasteryhub.modules.simulator.entity.GrantStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/** Droits d'usage du simulateur (administration). Un octroi manuel exige un motif : il est consigné dans l'audit. */
public final class GrantPayloads {

    private GrantPayloads() {}

    public record GrantRequest(
            @NotBlank @Email String learnerEmail,
            @NotNull PlanCode plan,
            @Min(1) @Max(365) int days,
            @Min(0) @Max(50) int examAttempts,
            @NotBlank @Size(max = 1000) String reason) {}

    public record RevokeRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record GrantView(UUID id, String learnerEmail, String learnerName, PlanCode plan, LocalDateTime startsAt,
                            LocalDateTime expiresAt, int examAttemptsTotal, int examAttemptsUsed, GrantStatus status,
                            String grantedBy, String reason, LocalDateTime createdAt, LocalDateTime revokedAt, String revokeReason) {}
}
