package com.modeltech.datamasteryhub.modules.training.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Partenaire tel qu'exposé publiquement (contrat Partner de formation.type.ts).
 * Ne contient JAMAIS la part de revenu ni le contact interne.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PartnerResponse {
    private UUID id;
    private String slug;
    private String name;
    private String logoUrl;
    private String bio;
    private String website;
}
