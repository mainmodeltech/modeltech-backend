package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Retrouve la stratégie d'un type de question. Un type sans stratégie est une erreur de démarrage, pas de requête. */
@Component
public class ItemTypeRegistry {

    private final Map<ItemType, ItemTypeStrategy> byType = new EnumMap<>(ItemType.class);

    public ItemTypeRegistry(List<ItemTypeStrategy> strategies) {
        strategies.forEach(s -> byType.put(s.type(), s));
        for (ItemType t : ItemType.values()) {
            if (!byType.containsKey(t)) throw new IllegalStateException("Aucune stratégie pour le type de question " + t);
        }
    }

    public Map<String, Object> validateAndNormalize(ItemType type, Map<String, Object> payload) {
        return byType.get(type).validateAndNormalize(payload);
    }

    public Map<String, Object> learnerView(ItemType type, Map<String, Object> payload) {
        return byType.get(type).learnerView(payload);
    }

    public Map<String, Object> normalizeAnswer(ItemType type, Map<String, Object> payload, Map<String, Object> answer) {
        return byType.get(type).normalizeAnswer(payload, answer);
    }

    public double score(ItemType type, Map<String, Object> payload, Map<String, Object> answer) {
        return byType.get(type).score(payload, answer);
    }

    public Map<String, Object> correctAnswer(ItemType type, Map<String, Object> payload) {
        return byType.get(type).correctAnswer(payload);
    }

    public Map<String, Object> review(ItemType type, Map<String, Object> payload, Map<String, Object> answer) {
        return byType.get(type).review(payload, answer);
    }
}
