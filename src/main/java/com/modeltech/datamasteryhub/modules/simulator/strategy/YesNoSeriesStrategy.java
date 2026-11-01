package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Série Oui/Non : 2 à 6 affirmations, chacune notée vraie ou fausse. */
@Component
class YesNoSeriesStrategy implements ItemTypeStrategy {

    private static final String STATEMENT_ID = "^S[1-6]$";

    @Override
    public ItemType type() {
        return ItemType.YES_NO_SERIES;
    }

    @Override
    public Map<String, Object> validateAndNormalize(Map<String, Object> payload) {
        List<Map<String, Object>> raw = PayloadReader.objects(payload, "statements", 2, 6);
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> statements = new ArrayList<>();
        for (Map<String, Object> s : raw) {
            String id = PayloadReader.id(s, STATEMENT_ID, "Affirmation");
            if (!ids.add(id)) throw new InvalidItemException("Affirmation " + id + " : identifiant en double.");
            statements.add(PayloadReader.item(
                    "id", id,
                    "text", PayloadReader.text(s, "text", "Affirmation " + id),
                    "answer", PayloadReader.flag(s, "answer", "Affirmation " + id)));
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("statements", statements);
        return normalized;
    }
}
