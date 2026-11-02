package com.modeltech.datamasteryhub.modules.simulator.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Demande d'essai gratuit du simulateur (formulaire public). */
public final class TrialPayloads {

    private TrialPayloads() {}

    public record TrialRequest(
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 255) String email,
            @Size(max = 50) String phone,
            @AssertTrue(message = "Votre accord pour être contacté est obligatoire") boolean consent) {}
}
