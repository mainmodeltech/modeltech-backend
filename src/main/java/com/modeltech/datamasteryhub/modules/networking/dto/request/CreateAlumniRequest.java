// ── CreateAlumniRequest.java ──────────────────────────────────────────────────
package com.modeltech.datamasteryhub.modules.networking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Données pour créer un alumni")
public class CreateAlumniRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 255)
    private String name;

    @Email(message = "Email invalide")
    private String email;

    private String phone;

    @Size(max = 255)
    private String currentTitle;

    @Size(max = 255)
    private String currentPosition;

    private String linkedinUrl;
    private String photoUrl;        // URL externe ou MinIO (upload séparé)
    private String cohort;
    private Integer year;

    @Size(max = 255)
    private String bootcampTitle;

    /** Lien optionnel vers une inscription existante */
    private UUID registrationId;

    private boolean published = true;
    private int displayOrder  = 0;
}
