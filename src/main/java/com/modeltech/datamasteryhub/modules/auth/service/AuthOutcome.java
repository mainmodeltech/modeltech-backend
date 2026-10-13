package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;

import java.time.Duration;

/**
 * Résultat d'une ouverture ou d'un rafraîchissement de session : le corps JSON, et le jeton de rafraîchissement à
 * déposer dans le cookie {@code HttpOnly} (null : ne pas toucher au cookie, ex. défi MFA ou onglets simultanés).
 */
public record AuthOutcome(AuthResponse body, String refreshToken, Duration refreshMaxAge) {

    public static AuthOutcome withoutCookie(AuthResponse body) {
        return new AuthOutcome(body, null, null);
    }
}
