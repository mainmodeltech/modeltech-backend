package com.modeltech.datamasteryhub.modules.communication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactMessageRequestDTO {
    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    private String phone;
    private String company;
    private String subject;

    @NotBlank(message = "Le message ne peut pas être vide")
    private String message;
}
