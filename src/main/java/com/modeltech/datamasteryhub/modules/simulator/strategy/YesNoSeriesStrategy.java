package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Série Oui/Non : 2 à 6 affirmations, chacune notée vraie ou fausse. Réponse {@code {"answers":{"S1":true,"S2":false}}} ;
 * la note est la part d'affirmations correctement jugées (une affirmation sans réponse vaut 0).
 */
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

    @Override
    public Map<String, Object> learnerView(Map<String, Object> payload) {
        List<Map<String, Object>> shown = new ArrayList<>();
        for (Map<String, Object> s : statements(payload)) shown.add(PayloadReader.item("id", s.get("id"), "text", s.get("text")));
        return Map.of("statements", shown);
    }

    @Override
    public Map<String, Object> normalizeAnswer(Map<String, Object> payload, Map<String, Object> answer) {
        if (answer == null || answer.isEmpty()) return null;
        if (!(answer.get("answers") instanceof Map<?, ?> given)) throw new InvalidItemException("Réponse invalide : « answers » attendu (objet).");
        Set<String> known = new HashSet<>();
        for (Map<String, Object> s : statements(payload)) known.add((String) s.get("id"));
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : given.entrySet()) {
            if (!(e.getKey() instanceof String id) || !known.contains(id)) throw new InvalidItemException("Réponse invalide : affirmation inconnue.");
            if (!(e.getValue() instanceof Boolean b)) throw new InvalidItemException("Réponse invalide : « " + id + " » doit être vrai ou faux.");
            clean.put(id, b);
        }
        return clean.isEmpty() ? null : Map.of("answers", clean);
    }

    @Override
    @SuppressWarnings("unchecked")
    public double score(Map<String, Object> payload, Map<String, Object> answer) {
        Map<String, Object> normalized = answer == null ? null : normalizeAnswer(payload, answer);
        if (normalized == null) return 0;
        Map<String, Object> given = (Map<String, Object>) normalized.get("answers");
        List<Map<String, Object>> all = statements(payload);
        int right = 0;
        for (Map<String, Object> s : all) {
            if (s.get("answer").equals(given.get(s.get("id")))) right++;
        }
        return (double) right / all.size();
    }

    @Override
    public Map<String, Object> correctAnswer(Map<String, Object> payload) {
        Map<String, Object> answers = new LinkedHashMap<>();
        for (Map<String, Object> s : statements(payload)) answers.put((String) s.get("id"), s.get("answer"));
        return Map.of("answers", answers);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> statements(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("statements");
    }
}
