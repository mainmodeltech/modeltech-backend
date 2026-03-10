package com.modeltech.datamasteryhub.modules.training.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatePromoCodeRequest {

    @NotBlank(message = "Le code est obligatoire")
    private String code;

    private String description;

    @NotBlank(message = "Le nom du parrain est obligatoire")
    private String referrerName;

    private String referrerEmail;
    private String referrerPhone;

    @NotNull(message = "Le pourcentage de reduction est obligatoire")
    @Min(value = 0, message = "Le pourcentage doit etre >= 0")
    @Max(value = 100, message = "Le pourcentage doit etre <= 100")
    private Integer discountPercent;

    /** Nombre max d'utilisations (null = illimite) */
    private Integer maxUses;

    /** Date d'expiration (null = pas d'expiration) */
    private LocalDateTime expiresAt;
}
