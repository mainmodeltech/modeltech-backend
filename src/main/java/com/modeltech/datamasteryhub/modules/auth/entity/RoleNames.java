package com.modeltech.datamasteryhub.modules.auth.entity;

import java.util.Set;

/** Noms des rôles tels que stockés en base (préfixe {@code ROLE_}, convention Spring Security). */
public final class RoleNames {

    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String EDITOR = "ROLE_EDITOR";
    public static final String TRAINER = "ROLE_TRAINER";
    public static final String PARTNER = "ROLE_PARTNER";
    public static final String LEARNER = "ROLE_LEARNER";

    // Profils par métier (migration V33)
    public static final String FINANCE = "ROLE_FINANCE";
    public static final String ADMISSIONS = "ROLE_ADMISSIONS";
    public static final String PEDAGOGY = "ROLE_PEDAGOGY";
    public static final String COMMUNICATION = "ROLE_COMMUNICATION";
    public static final String AUDITOR = "ROLE_AUDITOR";

    /**
     * Profils livrés pour le back-office. Ce n'est PLUS la liste des profils du personnel : le SUPER_ADMIN peut en créer
     * d'autres (colonne {@code roles.is_staff}) ; utiliser {@code Role#isStaff()}.
     */
    public static final Set<String> STAFF = Set.of(SUPER_ADMIN, ADMIN, EDITOR, TRAINER, PARTNER,
            FINANCE, ADMISSIONS, PEDAGOGY, COMMUNICATION, AUDITOR);

    private RoleNames() {}

    /** « admin » ou « ROLE_ADMIN » → « ROLE_ADMIN ». */
    public static String normalize(String role) {
        String upper = role.trim().toUpperCase();
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }
}
