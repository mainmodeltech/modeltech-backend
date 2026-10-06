package com.modeltech.datamasteryhub.modules.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/** Vue SUPER_ADMIN d'un compte de back-office — jamais de hash de mot de passe. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminUserSummaryResponse {
    private UUID id;
    private String email;
    private String fullName;
    private Set<String> roles;
    private boolean active;
    private UUID partnerId;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
