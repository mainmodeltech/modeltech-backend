package com.modeltech.datamasteryhub.modules.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Envoyer un e-mail de test pour vérifier la messagerie")
public class TestEmailRequest {

    @Email(message = "Email invalide")
    @NotBlank(message = "L'adresse est obligatoire")
    private String to;
}
