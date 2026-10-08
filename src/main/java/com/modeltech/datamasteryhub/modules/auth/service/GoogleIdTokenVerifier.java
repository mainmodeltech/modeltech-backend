package com.modeltech.datamasteryhub.modules.auth.service;

import java.util.Optional;

/** Vérifie un « ID token » Google (bouton « Se connecter avec Google » du front). */
public interface GoogleIdTokenVerifier {

    /** Identité vérifiée dans le jeton. */
    record GoogleIdentity(String email, boolean emailVerified, String name) {}

    /** Le jeton est valide (signature Google, audience = notre client, non expiré) ; vide sinon. */
    Optional<GoogleIdentity> verify(String idToken);

    /** Identifiant client OAuth configuré ({@code GOOGLE_CLIENT_ID}) ; vide = connexion Google désactivée. */
    String clientId();

    default boolean enabled() {
        return clientId() != null && !clientId().isBlank();
    }
}
