package com.modeltech.datamasteryhub.modules.payments.dto;

import com.modeltech.datamasteryhub.modules.payments.PayableService;
import com.modeltech.datamasteryhub.modules.payments.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Réglages de paiement (administration, réservés à la Direction). Aucune clé secrète n'y figure, jamais. */
public final class PaymentSettingsPayloads {

    private PaymentSettingsPayloads() {}

    public record ServiceSettingView(PayableService service, String label, PaymentMode mode, String modeLabel, LocalDateTime updatedAt, String updatedBy) {}

    /** @param degraded la passerelle est en panne détectée : le manuel est proposé automatiquement quelques minutes */
    public record GatewayInfo(String provider, boolean configured, String mode, boolean degraded, String notificationUrl) {}

    /** Réglage fait par variables d'environnement (PAYMENTS_FEES_*) : affiché pour information, non modifiable ici. */
    public record FeesInfo(String mode, BigDecimal percent, int fixedXof) {}

    public record PaymentSettingsView(boolean onlineEnabled, LocalDateTime updatedAt, String updatedBy, List<ServiceSettingView> services,
                                      GatewayInfo gateway, FeesInfo fees) {}

    /** Seuls les services présents dans {@code modes} sont modifiés ; le motif est obligatoire (consigné dans l'audit). */
    public record PaymentSettingsUpdate(@NotNull Boolean onlineEnabled, Map<PayableService, PaymentMode> modes, @NotBlank @Size(max = 500) String reason) {}
}
