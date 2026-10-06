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

    /** Rôles des comptes de back-office (table admin_users). */
    public static final Set<String> STAFF = Set.of(SUPER_ADMIN, ADMIN, EDITOR, TRAINER, PARTNER);

    private RoleNames() {}

    /** « admin » ou « ROLE_ADMIN » → « ROLE_ADMIN ». */
    public static String normalize(String role) {
        String upper = role.trim().toUpperCase();
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }
}
