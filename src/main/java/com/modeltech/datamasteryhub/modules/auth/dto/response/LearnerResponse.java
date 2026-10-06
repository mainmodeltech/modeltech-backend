package com.modeltech.datamasteryhub.modules.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Vue admin d'un apprenant — jamais de hash de mot de passe ni de jeton. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LearnerResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String country;
    private boolean active;
    /** true une fois que l'apprenant a défini son mot de passe. */
    private boolean passwordSet;
    private LocalDateTime emailVerifiedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
