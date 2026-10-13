package com.modeltech.datamasteryhub.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    /** Jeton d'accès de courte durée ; absent tant qu'un second facteur reste à valider. */
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private AdminUserResponse user;

    /** Personnel : le code de l'application d'authentification est à saisir ({@link #mfaToken} identifie la demande). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean mfaRequired;

    /** Personnel : la double authentification est obligatoire et n'est pas encore configurée sur ce compte. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean mfaEnrollmentRequired;

    /** Défi à renvoyer avec le code (valable 5 minutes, utilisable uniquement sur les routes /auth/mfa/*). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String mfaToken;

    /** Codes de secours, affichés une seule fois à la fin de l'enrôlement. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private java.util.List<String> recoveryCodes;

    /** Renseigné quand la connexion a utilisé un code de secours : combien en reste-t-il. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long recoveryCodesRemaining;

    /** Profil du compte connecté (back-office ou apprenant — le nom de classe est historique). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminUserResponse {
        private UUID id;
        private String email;
        private String fullName;
        private String primaryRole;       // rôle principal (compatibilité)
        private Set<String> roles;        // tous les rôles RBAC (ex. ROLE_ADMIN)

        /** « ADMIN » (back-office) ou « LEARNER » (apprenant). */
        private String userType;

        /** Personnel : la double authentification est activée. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean mfaEnabled;

        /** Partenaire rattaché (comptes ROLE_PARTNER uniquement). */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private UUID partnerId;
    }
}
