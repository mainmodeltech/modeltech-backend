package com.modeltech.datamasteryhub.modules.training.enums;

/**
 * Format d'une formation (catalogue). Valeurs alignées sur le contrat du front
 * (formation.type.ts) ; distinct de {@link SessionFormat} (REMOTE/HYBRID) qui
 * décrit le format d'une session.
 */
public enum FormationFormat {
    PRESENTIEL,
    EN_LIGNE,
    HYBRIDE
}
