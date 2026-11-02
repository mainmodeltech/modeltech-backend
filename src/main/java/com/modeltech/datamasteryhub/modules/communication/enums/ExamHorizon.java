package com.modeltech.datamasteryhub.modules.communication.enums;

/** Quand la personne compte passer l'examen (liste d'attente du simulateur). */
public enum ExamHorizon {
    WITHIN_1_MONTH("Dans le mois"),
    WITHIN_3_MONTHS("Dans les 3 mois"),
    LATER("Plus tard"),
    UNDECIDED("Je ne sais pas encore");

    private final String label;

    ExamHorizon(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
