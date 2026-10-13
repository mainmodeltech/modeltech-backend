package com.modeltech.datamasteryhub.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/**
 * Cookie du jeton de rafraîchissement : {@code HttpOnly} (illisible par le JavaScript de la page), {@code Secure},
 * {@code SameSite=Strict} et limité au chemin {@code /api/v1/auth}. Le site et l'API sont sur le même site
 * (model-technologie.com), donc Strict convient ; {@code app.auth.cookie.secure=false} uniquement en développement local en HTTP.
 *
 * <p>Comme ce cookie authentifie le rafraîchissement et la déconnexion, ces deux routes exigent un en-tête
 * {@code Origin} appartenant aux origines autorisées (défense contre les requêtes forgées depuis un autre site).
 */
@Component
public class RefreshCookie {

    public static final String NAME = "mt_refresh";
    public static final String PATH = "/api/v1/auth";

    @Value("${app.auth.cookie.secure:true}")
    private boolean secure;

    @Value("${app.auth.cookie.same-site:Strict}")
    private String sameSite;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    public void write(HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, token)
                .httpOnly(true).secure(secure).sameSite(sameSite).path(PATH).maxAge(maxAge).build().toString());
    }

    public void clear(HttpServletResponse response) {
        write(response, "", Duration.ZERO);
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }

    /** Refuse la requête si elle ne vient pas d'une page de l'un de nos sites. */
    public void requireTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origine non autorisée.");
        }
    }
}
