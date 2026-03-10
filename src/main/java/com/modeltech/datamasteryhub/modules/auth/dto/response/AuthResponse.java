package com.modeltech.datamasteryhub.modules.auth.dto.response;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminUserResponse {
        private UUID id;
        private String email;
        private String fullName;
        private String primaryRole;       // rôle principal (compatibilité)
        private Set<String> roles;        // tous les rôles RBAC
    }
}