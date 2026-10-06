package com.modeltech.datamasteryhub.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private AdminUserResponse user;

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

        /** Partenaire rattaché (comptes ROLE_PARTNER uniquement). */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private UUID partnerId;
    }
}
