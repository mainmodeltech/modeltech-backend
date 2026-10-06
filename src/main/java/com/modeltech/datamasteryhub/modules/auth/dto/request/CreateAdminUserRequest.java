package com.modeltech.datamasteryhub.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Créer un compte de back-office (un e-mail d'invitation lui est envoyé)")
public class CreateAdminUserRequest {

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Le nom complet est obligatoire")
    @Size(max = 255)
    private String fullName;

    @Schema(description = "SUPER_ADMIN, ADMIN, EDITOR, TRAINER ou PARTNER (avec ou sans préfixe ROLE_)")
    @NotEmpty(message = "Au moins un rôle est obligatoire")
    private List<String> roles;

    @Schema(description = "Obligatoire pour le rôle PARTNER : partenaire dont le compte gère les formations")
    private UUID partnerId;
}
