package com.modeltech.datamasteryhub.modules.audit.service;

/**
 * Les actions consignées dans le journal d'audit : celles qui engagent de l'argent, une décision sur une personne, des
 * droits ou ce que le public voit. Ajouter une action = une valeur ici, puis l'appel dans le service concerné.
 */
public enum AuditAction {

    // ── Paiements ──
    PAYMENT_RECORDED("Paiements", "Paiement saisi (reçu hors site)"),
    PAYMENT_CONFIRMED("Paiements", "Paiement confirmé"),
    PAYMENT_REJECTED("Paiements", "Paiement refusé"),
    REFUND_REQUESTED("Paiements", "Remboursement demandé"),
    REFUND_APPROVED("Paiements", "Remboursement effectué"),
    REFUND_DECLINED("Paiements", "Demande de remboursement refusée ou retirée"),
    INVOICE_CREATED("Paiements", "Facture créée"),
    INVOICE_CANCELLED("Paiements", "Facture annulée"),

    // ── Admissions ──
    REGISTRATION_ACCEPTED("Admissions", "Candidature acceptée"),
    REGISTRATION_REJECTED("Admissions", "Candidature refusée"),
    REGISTRATION_CANCELLED("Admissions", "Inscription annulée"),
    REGISTRATION_STATUS_CHANGED("Admissions", "Statut d'une inscription modifié"),
    REGISTRATION_DELETED("Admissions", "Candidature supprimée"),
    ENROLLMENT_CREATED("Admissions", "Inscription saisie manuellement"),
    LEARNER_CREATED("Admissions", "Compte apprenant créé"),
    LEARNER_ACTIVATION_CHANGED("Admissions", "Compte apprenant activé ou désactivé"),

    // ── Tarifs et parrainage ──
    PRICE_CHANGED("Tarifs", "Prix modifié"),
    PROMO_CHANGED("Tarifs", "Code de parrainage créé, modifié ou supprimé"),
    PARTNER_REVENUE_CHANGED("Tarifs", "Part de revenu d'un partenaire modifiée"),

    // ── Pédagogie ──
    CERTIFICATE_ISSUED("Pédagogie", "Certificat délivré"),
    CERTIFICATE_VALIDATED("Pédagogie", "Certificat validé et envoyé"),
    CERTIFICATE_REVOKED("Pédagogie", "Certificat révoqué"),
    QUIZ_ATTEMPTS_GRANTED("Pédagogie", "Tentatives de quiz accordées"),

    // ── Comptes et droits ──
    ACCOUNT_CREATED("Comptes et droits", "Compte du back-office créé"),
    ACCOUNT_UPDATED("Comptes et droits", "Compte du back-office modifié (profils, activation)"),
    ACCOUNT_MFA_RESET("Comptes et droits", "Double authentification réinitialisée"),
    PROFILE_CHANGED("Comptes et droits", "Profil ou droits modifiés"),

    // ── Site ──
    SITE_CONTENT_CHANGED("Site", "Contenu du site modifié");

    private final String category;
    private final String label;

    AuditAction(String category, String label) {
        this.category = category;
        this.label = label;
    }

    public String category() {
        return category;
    }

    public String label() {
        return label;
    }
}
