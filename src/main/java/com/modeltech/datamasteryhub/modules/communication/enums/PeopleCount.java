package com.modeltech.datamasteryhub.modules.communication.enums;

/** Nombre de personnes à former (diagnostic entreprise). Le libellé est celui affiché par le site. */
public enum PeopleCount {
    RANGE_1_5("1 à 5"),
    RANGE_6_15("6 à 15"),
    RANGE_16_50("16 à 50"),
    OVER_50("Plus de 50");

    private final String label;

    PeopleCount(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
