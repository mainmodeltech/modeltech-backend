package com.modeltech.datamasteryhub.modules.communication.enums;

/** Besoin principal d'une entreprise (diagnostic). Le libellé est celui affiché par le site. */
public enum TrainingNeed {
    EXCEL_UPGRADE("Mise à niveau Excel"),
    POWER_BI_CUSTOM("Power BI sur mesure"),
    DATA_PATH_CUSTOM("Parcours data sur mesure"),
    UNDECIDED("Je ne sais pas encore");

    private final String label;

    TrainingNeed(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
