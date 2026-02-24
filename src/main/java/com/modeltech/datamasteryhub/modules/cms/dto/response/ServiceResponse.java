package com.modeltech.datamasteryhub.modules.cms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {

    private UUID id;
    private String title;
    private String description;
    private String iconName;
    private List<String> features;
    private String duration;
    private Integer displayOrder;
    private boolean published;

    // Champs d'audit
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
