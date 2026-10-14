package com.modeltech.datamasteryhub.modules.auth.permission;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catalogue des droits du back-office. Un <b>profil</b> (rôle) est un ensemble de ces droits, modifiable par le
 * SUPER_ADMIN ; un compte peut cumuler plusieurs profils (ses droits sont l'union des leurs). Le nom du droit est ce que
 * porte l'autorité Spring Security ({@code hasAuthority("PAYMENTS_MANAGE")}).
 *
 * <p>Convention : {@code *_VIEW} lit, {@code *_MANAGE} agit. Les droits de portée ({@code SCOPE_*}) ne donnent accès à
 * aucune route : ils décident si la personne voit <i>tout</i> (toutes les sessions, toutes les formations) ou seulement
 * ce qui lui est confié (formateur : ses sessions, partenaire : ses formations).
 *
 * <p>Le catalogue est fixé par le code : ajouter un droit = ajouter une valeur ici, le rattacher à ses routes dans
 * {@link AdminAccessRules} et, si besoin, à un profil par défaut dans {@link DefaultProfiles}.
 */
public enum Permission {

    // ── Tableaux de bord ───────────────────────────────────────────
    DASHBOARD_FINANCE("Tableau de bord", "Tableau de bord financier (montants encaissés, en retard, par formation)"),
    DASHBOARD_ACTIVITY("Tableau de bord", "Tableau de bord d'activité (volumes, remplissage des sessions — sans montants)"),

    // ── Admissions ─────────────────────────────────────────────────
    APPLICATIONS_VIEW("Admissions", "Voir les candidatures"),
    APPLICATIONS_DECIDE("Admissions", "Accepter, refuser ou annuler une candidature (au tarif du catalogue)"),
    ENROLLMENTS_MANAGE("Admissions", "Inscrire manuellement un apprenant"),
    PRICE_OVERRIDE("Admissions", "Déroger au tarif d'une inscription (montant, remise, échéances sur mesure)"),

    // ── Argent ─────────────────────────────────────────────────────
    PAYMENTS_VIEW("Paiements et facturation", "Voir les paiements et leur statut"),
    PAYMENTS_RECORD("Paiements et facturation", "Saisir un paiement reçu hors site (virement, espèces…) : il reste à confirmer par une autre personne"),
    PAYMENTS_MANAGE("Paiements et facturation", "Confirmer, refuser, relancer un paiement"),
    PAYMENTS_REFUND("Paiements et facturation", "Demander le remboursement d'un paiement"),
    PAYMENTS_REFUND_APPROVE("Paiements et facturation", "Valider ou refuser une demande de remboursement faite par une autre personne"),
    INVOICES_VIEW("Paiements et facturation", "Voir et télécharger les factures"),
    INVOICES_MANAGE("Paiements et facturation", "Créer, envoyer ou annuler une facture"),
    PROMO_VIEW("Paiements et facturation", "Voir les codes de parrainage"),
    PROMO_MANAGE("Paiements et facturation", "Créer et modifier les codes de parrainage"),
    PRICING_MANAGE("Paiements et facturation", "Fixer les prix des formations et des sessions"),

    // ── Partenaires ────────────────────────────────────────────────
    PARTNERS_VIEW("Partenaires", "Voir les partenaires"),
    PARTNERS_MANAGE("Partenaires", "Modifier la fiche et le logo d'un partenaire"),
    PARTNERS_REVENUE("Partenaires", "Fixer la part de revenu d'un partenaire"),

    // ── Apprenants ─────────────────────────────────────────────────
    LEARNERS_VIEW("Apprenants", "Voir les comptes apprenants"),
    LEARNERS_MANAGE("Apprenants", "Inviter, activer ou désactiver un apprenant"),

    // ── Pédagogie ──────────────────────────────────────────────────
    CATALOG_VIEW("Pédagogie", "Voir le catalogue (formations, domaines, sessions)"),
    CATALOG_MANAGE("Pédagogie", "Gérer le catalogue (formations, domaines, sessions, formateur d'une session)"),
    PROGRAM_MANAGE("Pédagogie", "Éditer le programme d'une formation (modules, leçons, quiz, projet final)"),
    SESSIONS_VIEW("Pédagogie", "Voir le suivi d'une session (progression, appel, questions)"),
    SESSIONS_TRACK("Pédagogie", "Animer une session : appel, correction des projets, réponses, messages"),
    SESSIONS_ASSIGN("Pédagogie", "Affecter un formateur à une session"),
    CERTIFICATES_VIEW("Pédagogie", "Voir les certificats"),
    CERTIFICATES_MANAGE("Pédagogie", "Délivrer, renvoyer ou révoquer un certificat"),
    CERTIFICATES_OVERRIDE("Pédagogie", "Délivrer un certificat par dérogation (conditions non remplies)"),
    SCOPE_ALL_SESSIONS("Portée", "Accéder à toutes les sessions (sinon : seulement celles qui vous sont confiées)"),
    SCOPE_ALL_FORMATIONS("Portée", "Éditer toutes les formations (sinon : seulement celles de votre organisme)"),

    // ── Relations et contenu ───────────────────────────────────────
    MASTERCLASS_VIEW("Relations et contenu", "Voir les inscrits aux masterclass"),
    MASTERCLASS_MANAGE("Relations", "Gérer les masterclass : créer un événement, présences, écrire aux inscrits"),
    MESSAGES_MANAGE("Relations et contenu", "Traiter les messages entrants (contact, diagnostics, candidatures partenaires)"),
    NEWSLETTER_MANAGE("Relations et contenu", "Gérer la newsletter et ses abonnés"),
    COMMUNITY_MODERATE("Relations et contenu", "Modérer la communauté"),
    EMAIL_LOGS_VIEW("Relations et contenu", "Consulter le journal d'envoi des e-mails"),
    SITE_CONTENT_VIEW("Relations et contenu", "Voir les pages du site, témoignages, alumni et projets"),
    SITE_CONTENT_MANAGE("Relations et contenu", "Modifier les pages du site, témoignages, alumni et projets"),

    // ── Administration ─────────────────────────────────────────────
    ACCOUNTS_MANAGE("Administration", "Gérer les comptes du back-office (création, profils, désactivation, MFA)"),
    PROFILES_MANAGE("Administration", "Gérer les profils et leurs droits"),
    SYSTEM_MANAGE("Administration", "Réglages techniques (e-mail de test…)"),
    AUDIT_VIEW("Administration", "Consulter le journal d'audit");

    private final String group;
    private final String label;

    Permission(String group, String label) {
        this.group = group;
        this.label = label;
    }

    public String group() {
        return group;
    }

    public String label() {
        return label;
    }

    /** Autorité implicite de tout compte du personnel (accès à l'espace d'administration, « Mon espace »). */
    public static final String BACKOFFICE_ACCESS = "BACKOFFICE_ACCESS";

    public static Optional<Permission> find(String name) {
        return Arrays.stream(values()).filter(p -> p.name().equals(name)).findFirst();
    }

    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
