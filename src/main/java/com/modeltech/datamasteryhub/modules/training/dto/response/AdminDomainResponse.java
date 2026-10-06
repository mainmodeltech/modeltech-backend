package com.modeltech.datamasteryhub.modules.training.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminDomainResponse {
    private UUID id;
    private String slug;
    private String name;
    private String description;
    private String badge;
    private Boolean comingSoon;
    private Boolean visible;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
