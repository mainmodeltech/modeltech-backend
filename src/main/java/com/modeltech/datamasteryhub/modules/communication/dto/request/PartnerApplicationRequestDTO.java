package com.modeltech.datamasteryhub.modules.communication.dto.request;

import com.modeltech.datamasteryhub.modules.communication.enums.PartnerDomain;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Candidature d'un formateur partenaire (page /partenaires). */
@Data
@Schema(description = "Candidature d'un formateur partenaire")
public class PartnerApplicationRequestDTO {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100)
    private String firstName;

    /** Facultatif (un seul mot saisi dans « Nom et prénom »). */
    @Size(max = 100)
    private String lastName;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    /** Organisme (si applicable). */
    @Size(max = 255)
    private String organization;

    @Schema(description = "PROJECT_AGILE, APPLIED_AI, FINANCE_CONTROL, CYBERSECURITY, COMMUNICATION_LEADERSHIP ou OTHER")
    @NotNull(message = "Le domaine est obligatoire")
    private PartnerDomain domain;

    @Size(max = 255)
    private String linkedinUrl;

    /** Titre, public visé, durée envisagée. */
    @NotBlank(message = "La formation proposée est obligatoire")
    @Size(max = 500)
    private String proposal;

    /** Certifications, entreprises formées, nombre de participants… */
    @Size(max = 4000)
    private String references;
}
