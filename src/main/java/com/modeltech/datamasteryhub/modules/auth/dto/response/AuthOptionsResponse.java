package com.modeltech.datamasteryhub.modules.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Modes de connexion proposés par le serveur : le front n'affiche que ce qui est actif. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthOptionsResponse {
    private boolean password;
    private boolean passwordless;
    private boolean google;
    /** Identifiant client public à passer à Google Identity Services ; nul si Google est désactivé. */
    private String googleClientId;
}
