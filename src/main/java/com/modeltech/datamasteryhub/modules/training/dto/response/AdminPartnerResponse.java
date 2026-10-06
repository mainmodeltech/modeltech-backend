package com.modeltech.datamasteryhub.modules.training.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminPartnerResponse {
    private UUID id;
    private String slug;
    private String name;
    private String logoUrl;
    private String bio;
    private String website;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private BigDecimal revenueSharePercent;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
