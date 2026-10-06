package com.modeltech.datamasteryhub.modules.communication.enums;

/** Domaine proposé par un formateur partenaire (candidature). Le libellé est celui affiché par le site. */
public enum PartnerDomain {
    PROJECT_AGILE("Gestion de projet & Agile"),
    APPLIED_AI("IA appliquée"),
    FINANCE_CONTROL("Finance & contrôle de gestion"),
    CYBERSECURITY("Cybersécurité"),
    COMMUNICATION_LEADERSHIP("Communication & leadership"),
    OTHER("Autre domaine");

    private final String label;

    PartnerDomain(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
