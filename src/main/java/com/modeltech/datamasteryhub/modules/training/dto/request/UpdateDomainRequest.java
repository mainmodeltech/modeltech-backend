package com.modeltech.datamasteryhub.modules.training.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Mettre à jour un domaine (tous champs optionnels)")
public class UpdateDomainRequest {

    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug invalide (minuscules, chiffres et tirets)")
    private String slug;

    @Size(max = 150)
    private String name;

    private String description;

    @Size(max = 100)
    private String badge;

    private Boolean comingSoon;
    private Boolean visible;
    private Integer displayOrder;
}
