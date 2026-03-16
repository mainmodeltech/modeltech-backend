package com.modeltech.datamasteryhub.modules.networking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Données pour mettre à jour un projet (champs optionnels)")
public class UpdateProjectRequest {

    @Size(max = 255)
    private String title;

    private String       description;
    private List<String> toolsTechnologies;
    private String       accessLink;
    private String       coverImageUrl;
    private String       cohort;
    private Integer      year;
    private Boolean      published;
    private Integer      displayOrder;
}
