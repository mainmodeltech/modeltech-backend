package com.modeltech.datamasteryhub.modules.networking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Données pour créer un projet alumni")
public class CreateProjectRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 255)
    private String title;

    private String description;

    private List<String> toolsTechnologies;

    private String accessLink;
    private String coverImageUrl;
    private String cohort;
    private Integer year;

    private boolean published    = true;
    private int     displayOrder = 0;

    /**
     * IDs des alumni membres. Au moins un requis.
     * Format : [{alumniId, role, displayOrder}]
     */
    @NotEmpty(message = "Au moins un membre est requis")
    private List<ProjectMemberRequest> members;
}
