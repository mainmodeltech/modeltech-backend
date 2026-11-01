package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;

import java.util.Map;

/**
 * Règles propres à un type de question (Strategy) : valide la structure du {@code payload} et en renvoie une copie
 * normalisée (seuls les champs attendus, texte nettoyé). Ajouter un type de question n'oblige à modifier aucun
 * service : on ajoute une valeur de {@link ItemType} et une implémentation (ouvert/fermé).
 */
public interface ItemTypeStrategy {

    ItemType type();

    /** @throws InvalidItemException si la structure ou les règles du type ne sont pas respectées */
    Map<String, Object> validateAndNormalize(Map<String, Object> payload);
}
