package com.modeltech.datamasteryhub.modules.training.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdatePromoCodeRequest {

    private String code;
    private String description;
    private String referrerName;
    private String referrerEmail;
    private String referrerPhone;

    @Min(value = 0, message = "Le pourcentage doit etre >= 0")
    @Max(value = 30, message = "Un code de parrainage ne peut pas dépasser 30 % de remise")
    private Integer discountPercent;

    private Integer maxUses;
    private LocalDateTime expiresAt;
}
