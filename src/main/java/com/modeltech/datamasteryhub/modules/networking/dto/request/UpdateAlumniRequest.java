package com.modeltech.datamasteryhub.modules.networking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Données pour mettre à jour un alumni (champs optionnels)")
public class UpdateAlumniRequest {

    @Size(max = 255)
    private String name;

    @Email(message = "Email invalide")
    private String email;

    private String phone;
    private String currentTitle;
    private String currentPosition;
    private String linkedinUrl;
    private String photoUrl;
    private String cohort;
    private Integer year;
    private String bootcampTitle;
    private UUID   registrationId;
    private Boolean published;
    private Integer displayOrder;
}
