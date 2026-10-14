package com.modeltech.datamasteryhub.modules.audit.service;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Séparation des tâches : l'auteur d'une action sensible ne peut pas être celui qui la valide.
 *
 * <p>Règle de repli pour une petite équipe : si <b>personne d'autre</b> n'est habilité à valider (compte actif, connecté ces
 * derniers mois, portant le droit — la Direction comptant pour tous les droits), l'action reste possible mais est consignée « sans second contrôle ».
 * Dès qu'une deuxième personne habilitée existe, la règle s'applique sans rien reconfigurer.
 */
@Component
@RequiredArgsConstructor
public class FourEyes {

    private final AdminUserRepository adminUsers;

    /**
     * Un compte compte comme « autre personne habilitée » s'il s'est connecté récemment : un compte oublié (ancien compte de
     * démarrage, collaborateur parti) ne doit pas bloquer indéfiniment une petite équipe.
     */
    @org.springframework.beans.factory.annotation.Value("${app.four-eyes.active-days:90}")
    private int activeDays;

    /** Vrai s'il existe, hors {@code exceptEmail}, un compte actif qui détient {@code permission}. */
    @Transactional(readOnly = true)
    public boolean otherHolderExists(Permission permission, String exceptEmail) {
        String except = exceptEmail == null ? "" : exceptEmail.trim().toLowerCase(Locale.ROOT);
        LocalDateTime since = LocalDateTime.now().minusDays(activeDays);
        for (AdminUser u : adminUsers.findAllByActiveTrueAndIsDeletedFalse()) {
            if (u.getEmail().trim().toLowerCase(Locale.ROOT).equals(except)) continue;
            if (u.getLastLoginAt() == null || u.getLastLoginAt().isBefore(since)) continue;
            for (Role role : u.getRoles()) {
                if (RoleNames.SUPER_ADMIN.equals(role.getName()) || role.getPermissions().contains(permission.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean same(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}
