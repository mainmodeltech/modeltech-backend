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
 * Réponse libre validée par mots-clés : le candidat rédige quelques mots ou phrases ; la réponse est juste si elle contient
 * <b>tous</b> les mots-clés attendus (chacun peut avoir plusieurs formulations acceptées, ex. « mesure » / « measure »).
 * Option {@code partialCredit} : note proportionnelle aux mots-clés trouvés. Réponse {@code {"text":"..."}}.
 *
 * <p>Les mots-clés ne sortent jamais du serveur pendant la tentative : la vue apprenant ne porte que la taille maximale.
 */
@Component
class ShortAnswerStrategy implements ItemTypeStrategy {

    private static final String KEYWORD_ID = "^K[1-8]$";
    static final int DEFAULT_MAX_LENGTH = 600;
    private static final int MIN_MAX_LENGTH = 20;
    private static final int MAX_MAX_LENGTH = 2000;
    private static final int MAX_FORM_LENGTH = 60;

    @Override
    public ItemType type() {
        return ItemType.SHORT_ANSWER;
    }

    @Override
    public Map<String, Object> validateAndNormalize(Map<String, Object> payload) {
        List<Map<String, Object>> raw = PayloadReader.objects(payload, "keywords", 1, 8);
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> keywords = new ArrayList<>();
        for (Map<String, Object> k : raw) {
            String id = PayloadReader.id(k, KEYWORD_ID, "Mot-clé");
            if (!ids.add(id)) throw new InvalidItemException("Mot-clé " + id + " : identifiant en double.");
            if (!(k.get("accept") instanceof List<?> forms) || forms.isEmpty() || forms.size() > 6) {
                throw new InvalidItemException("Mot-clé " + id + " : indiquez de 1 à 6 formulations acceptées.");
            }
            List<String> accepted = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Object f : forms) {
                String form = f instanceof String s ? s.trim() : "";
                if (form.isEmpty() || form.length() > MAX_FORM_LENGTH) {
                    throw new InvalidItemException("Mot-clé " + id + " : chaque formulation fait de 1 à " + MAX_FORM_LENGTH + " caractères.");
                }
                if (KeywordMatcher.normalize(form).isEmpty()) {
                    throw new InvalidItemException("Mot-clé " + id + " : « " + form + " » ne contient aucune lettre ni chiffre.");
                }
                if (seen.add(KeywordMatcher.normalize(form))) accepted.add(form);
            }
            keywords.add(PayloadReader.item("id", id, "accept", accepted));
        }
        int maxLength = DEFAULT_MAX_LENGTH;
        if (payload.get("maxLength") != null) {
            if (!(payload.get("maxLength") instanceof Number n) || n.intValue() < MIN_MAX_LENGTH || n.intValue() > MAX_MAX_LENGTH) {
                throw new InvalidItemException("« maxLength » doit être compris entre " + MIN_MAX_LENGTH + " et " + MAX_MAX_LENGTH + ".");
            }
            maxLength = n.intValue();
        }
        boolean partial = payload.get("partialCredit") != null && PayloadReader.flag(payload, "partialCredit", "Réponse libre");

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("keywords", keywords);
        normalized.put("partialCredit", partial);
        normalized.put("maxLength", maxLength);
        return normalized;
    }

    @Override
    public Map<String, Object> learnerView(Map<String, Object> payload) {
        return Map.of("maxLength", maxLength(payload));
    }

    @Override
    public Map<String, Object> normalizeAnswer(Map<String, Object> payload, Map<String, Object> answer) {
        if (answer == null || answer.isEmpty()) return null;
        if (!(answer.get("text") instanceof String text)) throw new InvalidItemException("Réponse invalide : « text » attendu (texte).");
        String trimmed = text.strip();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > maxLength(payload)) {
            throw new InvalidItemException("Réponse trop longue : " + maxLength(payload) + " caractères au plus.");
        }
        return Map.of("text", trimmed);
    }

    @Override
    public double score(Map<String, Object> payload, Map<String, Object> answer) {
        Map<String, Object> normalized = answer == null ? null : normalizeAnswer(payload, answer);
        if (normalized == null) return 0;
        List<Map<String, Object>> all = keywords(payload);
        int found = foundIds(payload, (String) normalized.get("text")).size();
        if (found == all.size()) return 1.0;
        return Boolean.TRUE.equals(payload.get("partialCredit")) ? (double) found / all.size() : 0;
    }

    @Override
    public Map<String, Object> correctAnswer(Map<String, Object> payload) {
        List<Map<String, Object>> expected = new ArrayList<>();
        for (Map<String, Object> k : keywords(payload)) expected.add(PayloadReader.item("id", k.get("id"), "accept", k.get("accept")));
        return Map.of("keywords", expected);
    }

    /** Pour la correction : quels mots-clés la réponse contient et lesquels manquent. */
    @Override
    public Map<String, Object> review(Map<String, Object> payload, Map<String, Object> answer) {
        Map<String, Object> normalized = answer == null ? null : normalizeAnswer(payload, answer);
        List<String> found = normalized == null ? List.of() : foundIds(payload, (String) normalized.get("text"));
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> k : keywords(payload)) {
            if (!found.contains((String) k.get("id"))) missing.add((String) k.get("id"));
        }
        return PayloadReader.item("found", found, "missing", missing);
    }

    @SuppressWarnings("unchecked")
    private List<String> foundIds(Map<String, Object> payload, String text) {
        String normalizedText = KeywordMatcher.normalize(text);
        List<String> found = new ArrayList<>();
        for (Map<String, Object> k : keywords(payload)) {
            for (String form : (List<String>) k.get("accept")) {
                if (KeywordMatcher.contains(normalizedText, form)) {
                    found.add((String) k.get("id"));
                    break;
                }
            }
        }
        return found;
    }

    private static int maxLength(Map<String, Object> payload) {
        return payload.get("maxLength") instanceof Number n ? n.intValue() : DEFAULT_MAX_LENGTH;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> keywords(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("keywords");
    }
}
