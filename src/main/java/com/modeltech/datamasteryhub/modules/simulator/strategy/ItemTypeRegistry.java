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
}
