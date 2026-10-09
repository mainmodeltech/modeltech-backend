package com.modeltech.datamasteryhub.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Profil de l'apprenant connecté (JSON brut, comme le reste de {@code /learner/**}). */
public final class LearnerProfilePayloads {

    private LearnerProfilePayloads() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Profile {
        private String id;
        /** Identifiant de connexion : non modifiable. */
        private String email;
        private String firstName;
        private String lastName;
        private String fullName;
        private String phone;
        private String country;
        private boolean emailVerified;
        /** false pour un compte créé par invitation / lien de connexion : le front propose alors « Définir un mot de passe ». */
        private boolean hasPassword;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateProfileRequest {
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100)
        private String firstName;
        @Size(max = 100)
        private String lastName;
        @Size(max = 50)
        private String phone;
        @Size(max = 100)
        private String country;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SetPasswordRequest {
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 128, message = "Le mot de passe doit contenir au moins 8 caractères")
        private String newPassword;
    }
}
