package com.modeltech.datamasteryhub.modules.communication.dto.request;

import com.modeltech.datamasteryhub.modules.communication.enums.RequesterType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactMessageRequestDTO {
    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    /** Facultatif : un visiteur peut ne saisir qu'un seul mot dans « Nom et prénom ». */
    private String lastName;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    private String phone;
    private String company;
    private String subject;

    /** Particulier ou entreprise (bascule du formulaire de contact) — facultatif. */
    private RequesterType requesterType;

    @NotBlank(message = "Le message ne peut pas être vide")
    private String message;
}
