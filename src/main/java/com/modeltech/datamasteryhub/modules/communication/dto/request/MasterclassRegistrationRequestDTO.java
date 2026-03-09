package com.modeltech.datamasteryhub.modules.communication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MasterclassRegistrationRequestDTO {

    @NotBlank
    private String masterclassId;

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    private String email;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    private String phone;

    /** Étudiant | Professionnel | Entrepreneur | Autre */
    private String profile;

    private String company;
}
