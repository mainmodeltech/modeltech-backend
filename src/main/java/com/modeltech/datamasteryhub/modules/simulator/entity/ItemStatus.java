package com.modeltech.datamasteryhub.modules.simulator.entity;

/** Cycle de vie d'une version de question : DRAFT → IN_REVIEW → APPROVED → PUBLISHED → RETIRED. */
public enum ItemStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    PUBLISHED,
    RETIRED;

    /** Une version publiée ou retirée est figée (le déclencheur de la base l'impose aussi). */
    public boolean isFrozen() {
        return this == PUBLISHED || this == RETIRED;
    }
}
