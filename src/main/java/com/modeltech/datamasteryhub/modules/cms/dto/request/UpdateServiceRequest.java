package com.modeltech.datamasteryhub.modules.cms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Corps de la requete pour mettre a jour un service (tous champs optionnels)")
public class UpdateServiceRequest {

    @Size(max = 255, message = "Le titre ne doit pas depasser 255 caracteres")
    private String title;

    private String description;
    private String iconName;
    private List<String> features;
    private String duration;
    private Integer displayOrder;
    private Boolean published;
}
