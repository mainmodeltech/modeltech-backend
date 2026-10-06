package com.modeltech.datamasteryhub.modules.training.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Créer un domaine du catalogue")
public class CreateDomainRequest {

    @Schema(description = "Identifiant d'URL ; généré depuis le nom si absent")
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug invalide (minuscules, chiffres et tirets)")
    private String slug;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 150)
    private String name;

    private String description;

    @Size(max = 100)
    private String badge;

    @Builder.Default
    private Boolean comingSoon = false;

    @Builder.Default
    private Boolean visible = true;

    @Builder.Default
    private Integer displayOrder = 0;
}
