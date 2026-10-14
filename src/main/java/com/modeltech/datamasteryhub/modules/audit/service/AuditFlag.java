package com.modeltech.datamasteryhub.modules.audit.service;

/** Marque une ligne qui mérite l'attention de la direction ou de l'audit. */
public enum AuditFlag {
    /** L'action aurait dû être contrôlée par une autre personne, mais personne d'autre n'était habilité à le faire. */
    NO_SECOND_CHECK("Sans second contrôle"),
    /** Écart volontaire par rapport à la règle (tarif, conditions d'un certificat…). */
    OVERRIDE("Dérogation");

    private final String label;

    AuditFlag(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
