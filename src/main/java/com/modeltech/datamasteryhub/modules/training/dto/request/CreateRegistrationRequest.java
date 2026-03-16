package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.entity.RegistrationProfile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateRegistrationRequest {

    private UUID bootcampId;

    private UUID sessionId;

    private String bootcampTitle;

    /** Code promo/parrainage saisi par le visiteur (optionnel) */
    private String promoCode;

    @NotBlank(message = "Le prenom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    private String phone;
    // ── Champs nouveaux ───────────────────────────────────────────────────────

    /** Pays de provenance (ex: "Sénégal", "Côte d'Ivoire"…) */
    @NotBlank(message = "Le pays de provenance est obligatoire")
    private String country;

    /**
     * Profil de l'inscrit.
     * Obligatoire. La validation conditionnelle (school / company / position)
     * est effectuée dans {@code RegistrationService}.
     */
    @NotNull(message = "Le profil est obligatoire")
    private RegistrationProfile profile;

    /**
     * École ou institution — obligatoire si {@code profile == STUDENT}.
     * null autorisé ici ; la contrainte métier est dans le service.
     */
    private String school;

    // ── Champs professionnels (existants, sémantique élargie) ─────────────────

    /**
     * Organisation / entreprise.
     * Obligatoire si {@code profile == PROFESSIONAL}.
     * Optionnel si {@code profile == ENTREPRENEUR}.
     */
    private String company;

    /**
     * Poste actuel / secteur d'activité.
     * Obligatoire si {@code profile == PROFESSIONAL}.
     * Optionnel si {@code profile == ENTREPRENEUR}.
     */
    private String position;
    private String message;

    /**
     * Token reCAPTCHA v3.
     * Obligatoire — vérifié côté backend avant tout traitement.
     * Non persisté en base.
     */
    @NotBlank(message = "Le token reCAPTCHA est obligatoire")
    private String recaptchaToken;
}
