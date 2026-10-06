package com.modeltech.datamasteryhub.modules.communication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Demande d'abonnement à la newsletter")
public class NewsletterSubscribeRequest {

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    @Size(max = 255)
    private String email;

    @Schema(description = "Origine de l'inscription, ex. « ressources »")
    @Size(max = 50)
    private String source;
}
