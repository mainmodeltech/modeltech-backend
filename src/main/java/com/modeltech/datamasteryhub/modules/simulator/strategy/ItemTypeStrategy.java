package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;

import java.util.Map;

/**
 * Tout ce qui dépend du type d'une question (Strategy) : valider et normaliser son contenu, en produire la vue
 * <b>apprenant sans corrigé</b>, valider une réponse, la noter et exposer la bonne réponse pour la correction.
 * Ajouter un type de question n'oblige à modifier aucun service : une valeur de {@link ItemType} et une implémentation
 * (ouvert/fermé).
 */
public interface ItemTypeStrategy {

    ItemType type();

    /** @throws InvalidItemException si la structure ou les règles du type ne sont pas respectées */
    Map<String, Object> validateAndNormalize(Map<String, Object> payload);

    /** Vue envoyée au navigateur pendant la tentative : <b>aucun champ de correction</b> (test automatique dédié). */
    Map<String, Object> learnerView(Map<String, Object> payload);

    /** Valide la forme d'une réponse (identifiants connus, taille) et la renvoie normalisée ; {@code null} = sans réponse. */
    Map<String, Object> normalizeAnswer(Map<String, Object> payload, Map<String, Object> answer);

    /** Note de 0 à 1 (crédit partiel possible) ; une réponse absente vaut 0. */
    double score(Map<String, Object> payload, Map<String, Object> answer);

    /** La bonne réponse, dans le même format qu'une réponse d'apprenant (servie seulement à la correction). */
    Map<String, Object> correctAnswer(Map<String, Object> payload);

    /** Détail propre au type pour la correction (ex. mots-clés trouvés ou manquants) ; vide par défaut. Servi seulement à la correction. */
    default Map<String, Object> review(Map<String, Object> payload, Map<String, Object> answer) {
        return Map.of();
    }
}
