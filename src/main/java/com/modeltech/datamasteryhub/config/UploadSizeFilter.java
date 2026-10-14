package com.modeltech.datamasteryhub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Refuse tôt (413) un téléversement manifestement trop lourd pour sa route, avant que le serveur ne l'ait reçu en entier.
 * Le plafond technique de Spring est large (fichiers du projet final) ; ce filtre garde les autres routes, dont l'endpoint
 * public de preuve de paiement, à une taille raisonnable.
 */
@Component
public class UploadSizeFilter extends OncePerRequestFilter {

    /** Fichiers du projet final d'un apprenant : la taille réelle est contrôlée par formation (maxSizeMb). */
    private static final Pattern PROJECT_FILES = Pattern.compile("^/api/v1/learner/formations/[^/]+/project/files/?$");

    static final long PROJECT_LIMIT = 105L * 1024 * 1024;
    /** Images (5 Mo), logos, captures, preuves de paiement : une marge pour l'enveloppe multipart. */
    static final long DEFAULT_LIMIT = 6L * 1024 * 1024;

    /** Taille annoncée de la requête (-1 si inconnue). */
    private static long declaredLength(HttpServletRequest request) {
        long length = request.getContentLengthLong();
        if (length >= 0) return length;
        try {
            String header = request.getHeader("Content-Length");
            return header == null ? -1 : Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return type == null || !type.toLowerCase().startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = PROJECT_FILES.matcher(request.getRequestURI()).matches() ? PROJECT_LIMIT : DEFAULT_LIMIT;
        if (declaredLength(request) > limit) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(("{\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"Fichier trop volumineux (max "
                    + limit / (1024 * 1024) + " Mo).\"}").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
