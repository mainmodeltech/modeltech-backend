package com.modeltech.datamasteryhub.modules.training.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PromoCodeResponse {

    private UUID id;
    private String code;
    private String description;

    // Parrain
    private String referrerName;
    private String referrerEmail;
    private String referrerPhone;

    // Reduction
    private Integer discountPercent;

    // Limites & usage
    private Integer maxUses;
    private Integer usageCount;
    private Integer remainingUses;  // calcule : maxUses - usageCount (null si illimite)
    private LocalDateTime expiresAt;
    private Boolean expired;        // calcule

    // Etat
    private Boolean isActive;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
