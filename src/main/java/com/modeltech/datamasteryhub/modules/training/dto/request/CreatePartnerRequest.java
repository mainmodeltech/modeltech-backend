package com.modeltech.datamasteryhub.modules.training.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Créer un partenaire formateur")
public class CreatePartnerRequest {

    @Schema(description = "Identifiant d'URL ; généré depuis le nom si absent")
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug invalide (minuscules, chiffres et tirets)")
    private String slug;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 150)
    private String name;

    private String bio;

    @Size(max = 255)
    private String website;

    @Size(max = 150)
    private String contactName;

    @Email(message = "Email invalide")
    private String contactEmail;

    @Size(max = 50)
    private String contactPhone;

    @DecimalMin(value = "0.0", message = "La part de revenu doit être comprise entre 0 et 100")
    @DecimalMax(value = "100.0", message = "La part de revenu doit être comprise entre 0 et 100")
    private BigDecimal revenueSharePercent;

    @Builder.Default
    private Boolean active = true;
}
