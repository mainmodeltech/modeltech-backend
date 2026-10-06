package com.modeltech.datamasteryhub.modules.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Jeton reçu par e-mail (confirmation ou désinscription). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NewsletterTokenRequest {

    @NotBlank(message = "Le jeton est obligatoire")
    @Size(max = 64)
    private String token;
}
