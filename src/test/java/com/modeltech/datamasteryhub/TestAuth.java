package com.modeltech.datamasteryhub;

import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.permission.DefaultProfiles;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Authentification simulée pour les tests du back-office : porte les mêmes autorités qu'une vraie session (profil
 * {@code ROLE_*} + droits du profil + {@code BACKOFFICE_ACCESS}), calculées depuis les réglages par défaut.
 * {@code TestAuth.as("x@test.local", "ADMIN")} se comporte comme un compte ayant ce profil.
 */
public final class TestAuth {

    private TestAuth() {}

    /** Compte ayant un ou plusieurs profils ({@code "ADMIN"} ou {@code "ROLE_ADMIN"}). Un apprenant n'a aucun droit de back-office. */
    public static RequestPostProcessor as(String email, String... profiles) {
        return SecurityMockMvcRequestPostProcessors.user(email).authorities(authorities(profiles));
    }

    /** Compte portant exactement ces droits (profil sur mesure). */
    public static RequestPostProcessor withPermissions(String email, Permission... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_CUSTOM"));
        authorities.add(new SimpleGrantedAuthority(Permission.BACKOFFICE_ACCESS));
        for (Permission p : permissions) authorities.add(new SimpleGrantedAuthority(p.name()));
        return SecurityMockMvcRequestPostProcessors.user(email).authorities(authorities);
    }

    public static List<GrantedAuthority> authorities(String... profiles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        boolean staff = false;
        for (String profile : profiles) {
            String role = RoleNames.normalize(profile);
            authorities.add(new SimpleGrantedAuthority(role));
            if (RoleNames.LEARNER.equals(role)) continue;
            staff = true;
            Set<Permission> permissions = DefaultProfiles.permissionsOf(role);
            for (Permission p : permissions) authorities.add(new SimpleGrantedAuthority(p.name()));
        }
        if (staff) authorities.add(new SimpleGrantedAuthority(Permission.BACKOFFICE_ACCESS));
        return authorities;
    }
}
