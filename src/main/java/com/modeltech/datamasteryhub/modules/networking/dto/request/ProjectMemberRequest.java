package com.modeltech.datamasteryhub.modules.networking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ProjectMemberRequest {

    @NotNull(message = "L'ID de l'alumni est obligatoire")
    private UUID alumniId;

    private String role;
    private int    displayOrder = 0;
}
