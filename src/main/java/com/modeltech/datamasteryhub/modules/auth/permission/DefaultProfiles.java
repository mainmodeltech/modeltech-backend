package com.modeltech.datamasteryhub.modules.auth.permission;

import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.modeltech.datamasteryhub.modules.auth.permission.Permission.*;

/**
 * Profils livrés avec la plateforme et leurs droits de départ (migration V33). Le SUPER_ADMIN peut ensuite les ajuster
 * ou en créer d'autres depuis « Profils et droits » ; cette classe sert de référence pour « Rétablir les droits par
 * défaut », pour les tests (un test vérifie que la migration et cette définition ne divergent pas) et pour la
 * documentation.
 *
 * <p>Répartition retenue avec la direction (octobre 2026) : un profil par responsabilité, cumulables. Le SUPER_ADMIN
 * (Direction) a tous les droits, y compris ceux ajoutés plus tard : ils ne sont pas stockés.
 */
public final class DefaultProfiles {

    public record Profile(String name, String label, String description, Set<Permission> permissions) {}

    private static final Map<String, Profile> BY_NAME = new LinkedHashMap<>();

    static {
        add(RoleNames.SUPER_ADMIN, "Direction", "Tous les droits, y compris comptes, profils et journal d'audit. À réserver à deux personnes.",
                EnumSet.allOf(Permission.class));

        add(RoleNames.FINANCE, "Finance", "Paiements, factures, remboursements, tarifs, parrainage, parts de revenu des partenaires.",
                EnumSet.of(DASHBOARD_FINANCE, DASHBOARD_ACTIVITY, APPLICATIONS_VIEW, PRICE_OVERRIDE,
                        PAYMENTS_VIEW, PAYMENTS_MANAGE, PAYMENTS_REFUND, INVOICES_VIEW, INVOICES_MANAGE,
                        PROMO_VIEW, PROMO_MANAGE, PRICING_MANAGE, PARTNERS_VIEW, PARTNERS_REVENUE,
                        LEARNERS_VIEW, CATALOG_VIEW, CERTIFICATES_VIEW));

        add(RoleNames.ADMISSIONS, "Admissions", "Candidatures, inscriptions, apprenants et messages entrants ; voit le statut des paiements sans pouvoir les modifier.",
                EnumSet.of(DASHBOARD_ACTIVITY, APPLICATIONS_VIEW, APPLICATIONS_DECIDE, ENROLLMENTS_MANAGE, PAYMENTS_VIEW,
                        LEARNERS_VIEW, LEARNERS_MANAGE, CATALOG_VIEW, SESSIONS_VIEW, CERTIFICATES_VIEW,
                        MASTERCLASS_VIEW, MESSAGES_MANAGE, EMAIL_LOGS_VIEW));

        add(RoleNames.PEDAGOGY, "Pédagogie", "Formations, programme, sessions, évaluations, certificats, suivi et communauté.",
                EnumSet.of(DASHBOARD_ACTIVITY, CATALOG_VIEW, CATALOG_MANAGE, PROGRAM_MANAGE, SESSIONS_VIEW, SESSIONS_TRACK, SESSIONS_ASSIGN,
                        CERTIFICATES_VIEW, CERTIFICATES_MANAGE, CERTIFICATES_OVERRIDE, SCOPE_ALL_SESSIONS, SCOPE_ALL_FORMATIONS,
                        LEARNERS_VIEW, PARTNERS_VIEW, PARTNERS_MANAGE, MASTERCLASS_VIEW, MASTERCLASS_MANAGE, COMMUNITY_MODERATE, SITE_CONTENT_VIEW));

        add(RoleNames.COMMUNICATION, "Communication", "Site, newsletter, témoignages, alumni, masterclass, messages et communauté.",
                EnumSet.of(MASTERCLASS_VIEW, MASTERCLASS_MANAGE, MESSAGES_MANAGE, NEWSLETTER_MANAGE, COMMUNITY_MODERATE,
                        SITE_CONTENT_VIEW, SITE_CONTENT_MANAGE));

        add(RoleNames.TRAINER, "Formateur", "Animation de ses sessions uniquement : appel, correction des projets, réponses aux questions, messages.",
                EnumSet.of(SESSIONS_VIEW, SESSIONS_TRACK));

        add(RoleNames.PARTNER, "Partenaire", "Programme de ses formations uniquement.",
                EnumSet.of(PROGRAM_MANAGE));

        add(RoleNames.AUDITOR, "Lecture seule / Audit", "Voit les finances et le journal d'audit sans rien pouvoir modifier (expert-comptable, associé).",
                EnumSet.of(DASHBOARD_FINANCE, DASHBOARD_ACTIVITY, APPLICATIONS_VIEW, PAYMENTS_VIEW, INVOICES_VIEW, PROMO_VIEW,
                        PARTNERS_VIEW, CATALOG_VIEW, EMAIL_LOGS_VIEW, AUDIT_VIEW));

        // Profils d'avant la refonte, conservés pour que rien ne casse le jour de la bascule : à réduire ensuite.
        EnumSet<Permission> fullManagement = EnumSet.allOf(Permission.class);
        fullManagement.removeAll(EnumSet.of(ACCOUNTS_MANAGE, PROFILES_MANAGE, SYSTEM_MANAGE, AUDIT_VIEW));
        add(RoleNames.ADMIN, "Gestion complète (transition)", "Ancien profil « Administrateur » : tout sauf les comptes, les profils et l'audit. À remplacer par les profils par métier.",
                fullManagement);

        add(RoleNames.EDITOR, "Éditeur (ancien profil)", "Ancien profil « Éditeur de contenu » : catalogue, programmes, contenu du site, messages, newsletter.",
                EnumSet.of(CATALOG_VIEW, CATALOG_MANAGE, PROGRAM_MANAGE, SCOPE_ALL_FORMATIONS, PARTNERS_VIEW, PARTNERS_MANAGE,
                        MASTERCLASS_VIEW, MESSAGES_MANAGE, NEWSLETTER_MANAGE, SITE_CONTENT_VIEW, SITE_CONTENT_MANAGE));
    }

    private DefaultProfiles() {}

    private static void add(String name, String label, String description, Set<Permission> permissions) {
        BY_NAME.put(name, new Profile(name, label, description, permissions));
    }

    public static Map<String, Profile> all() {
        return BY_NAME;
    }

    /** Droits de départ d'un profil livré, vides pour un profil inconnu. */
    public static Set<Permission> permissionsOf(String roleName) {
        Profile profile = BY_NAME.get(roleName);
        return profile == null ? Set.of() : profile.permissions();
    }
}
