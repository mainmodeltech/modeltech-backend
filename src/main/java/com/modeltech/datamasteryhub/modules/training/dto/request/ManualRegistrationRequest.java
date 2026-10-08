package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.entity.RegistrationProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** « Inscription manuelle » du back-office : mêmes champs que le formulaire public, sans reCAPTCHA. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Créer une candidature au nom d'une personne (appel, visite, entreprise…). Elle reste « en attente » : on l'accepte ensuite comme les autres.")
public class ManualRegistrationRequest {

    private UUID bootcampId;
    private UUID sessionId;
    private String bootcampTitle;
    private String promoCode;

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    private String phone;

    @NotBlank(message = "Le pays de provenance est obligatoire")
    private String country;

    @NotNull(message = "Le profil est obligatoire")
    private RegistrationProfile profile;

    private String school;
    private String company;
    private String position;
    private String message;
}
