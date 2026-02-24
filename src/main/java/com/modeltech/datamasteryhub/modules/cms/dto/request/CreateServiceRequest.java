package com.modeltech.datamasteryhub.modules.cms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Corps de la requete pour creer un service")
public class CreateServiceRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 255, message = "Le titre ne doit pas depasser 255 caracteres")
    private String title;

    private String description;
    private String iconName;
    private List<String> features;
    private String duration;
    private Integer displayOrder;

    /** Utilise Boolean (wrapper) pour permettre null = valeur par defaut */
    private Boolean published;
}
