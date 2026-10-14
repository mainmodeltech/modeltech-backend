package com.modeltech.datamasteryhub.security;

import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * La personne qui effectue la requête en cours et ses droits. Sert aux contrôles « par champ » que la table des routes
 * ne peut pas exprimer (ex. modifier un prix dans un formulaire de formation que la pédagogie a le droit d'éditer).
 */
public final class Actor {

    private Actor() {}

    /** Autorités de la requête en cours (profils {@code ROLE_*} et droits), chargées en base à chaque requête. */
    public static java.util.Set<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? java.util.Set.of()
                : auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
    }

    /** Adresse e-mail de la personne connectée, ou « système » hors requête authentifiée (tâche planifiée). */
    public static String email() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() ? "système" : auth.getName();
    }

    public static boolean isSuperAdmin() {
        return authorities().contains("ROLE_SUPER_ADMIN");
    }

    public static boolean has(Permission permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission.name()));
    }

    /** Refuse (403) si la personne n'a pas le droit ; {@code what} complète le message : « … modifier {what} ». */
    public static void require(Permission permission, String what) {
        if (!has(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous n'avez pas le droit de modifier " + what + " (droit « " + permission.label() + " »).");
        }
    }
}
