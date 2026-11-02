package com.modeltech.datamasteryhub.modules.communication.enums;

/** Origine d'un message reçu via les formulaires du site. */
public enum ContactType {
    /** Formulaire de contact classique. */
    CONTACT,
    /** Demande de diagnostic gratuit d'une entreprise (page Entreprises). */
    DIAGNOSTIC,
    /** Candidature d'un formateur partenaire (page Partenaires). */
    PARTNER_APPLICATION,
    /** Liste d'attente du simulateur de certification (page Simulateur). */
    SIMULATOR_WAITLIST,
    /** Demande d'essai gratuit du simulateur (prospect). */
    SIMULATOR_TRIAL
}
