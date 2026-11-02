package com.modeltech.datamasteryhub.modules.communication.enums;

/** Forfait du simulateur de certification qui intéresse la personne (liste d'attente). Le libellé est celui affiché par le site. */
public enum SimulatorPlan {
    ESSENTIEL("Essentiel"),
    PRO("Pro"),
    PRO_COACHING("Pro + coaching"),
    ENTREPRISE("Entreprise ou école"),
    UNDECIDED("Je ne sais pas encore");

    private final String label;

    SimulatorPlan(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
