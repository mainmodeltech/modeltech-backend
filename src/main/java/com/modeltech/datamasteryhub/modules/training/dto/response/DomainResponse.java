package com.modeltech.datamasteryhub.modules.training.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Domaine tel qu'exposé publiquement (contrat Domain de formation.type.ts). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DomainResponse {
    private UUID id;
    private String slug;
    private String name;
    private String description;
    private String badge;
    private Boolean comingSoon;
    private Integer displayOrder;
}
