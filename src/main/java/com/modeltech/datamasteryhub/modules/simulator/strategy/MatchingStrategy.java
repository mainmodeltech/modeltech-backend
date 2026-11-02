package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Correspondance (glisser-déposer de l'examen) : 2 à 6 éléments à associer chacun à une option parmi 2 à 8 (des options en
 * trop servent de leurres). Réponse {@code {"pairs":{"P1":"O3","P2":"O1"}}} ; la note est la part d'éléments correctement
 * associés (un élément sans option vaut 0). Par défaut une option ne se place qu'une fois ({@code reuse=false}).
 *
 * <p>Le corrigé ({@code pairs}) n'est jamais envoyé pendant la tentative, et les options sont <b>mélangées</b> à chaque
 * affichage : l'ordre saisi par l'auteur (souvent celui des bonnes réponses) ne doit pas trahir la solution.
 */
@Component
class MatchingStrategy implements ItemTypeStrategy {

    private static final String PROMPT_ID = "^P[1-6]$";
    private static final String OPTION_ID = "^O[1-8]$";

    private final Random random = new SecureRandom();

    @Override
    public ItemType type() {
        return ItemType.MATCHING;
    }

    @Override
    public Map<String, Object> validateAndNormalize(Map<String, Object> payload) {
        List<Map<String, Object>> rawPrompts = PayloadReader.objects(payload, "prompts", 2, 6);
        List<Map<String, Object>> rawOptions = PayloadReader.objects(payload, "options", 2, 8);
        boolean reuse = payload.get("reuse") != null && PayloadReader.flag(payload, "reuse", "Correspondance");

        Set<String> promptIds = new HashSet<>();
        List<Map<String, Object>> prompts = new ArrayList<>();
        for (Map<String, Object> p : rawPrompts) {
            String id = PayloadReader.id(p, PROMPT_ID, "Élément");
            if (!promptIds.add(id)) throw new InvalidItemException("Élément " + id + " : identifiant en double.");
            prompts.add(PayloadReader.item("id", id, "text", PayloadReader.text(p, "text", "Élément " + id)));
        }
        Set<String> optionIds = new HashSet<>();
        Set<String> optionTexts = new HashSet<>();
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> o : rawOptions) {
            String id = PayloadReader.id(o, OPTION_ID, "Option");
            if (!optionIds.add(id)) throw new InvalidItemException("Option " + id + " : identifiant en double.");
            String text = PayloadReader.text(o, "text", "Option " + id);
            if (!optionTexts.add(text.toLowerCase())) throw new InvalidItemException("Option " + id + " : deux options ont le même texte.");
            options.add(PayloadReader.item("id", id, "text", text));
        }
        if (!reuse && options.size() < prompts.size()) {
            throw new InvalidItemException("Il faut au moins autant d'options que d'éléments (une option ne se place qu'une fois).");
        }

        if (!(payload.get("pairs") instanceof Map<?, ?> rawPairs)) {
            throw new InvalidItemException("Le champ « pairs » est obligatoire : l'option attendue pour chaque élément.");
        }
        Map<String, Object> pairs = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Map<String, Object> p : prompts) {
            String id = (String) p.get("id");
            Object target = rawPairs.get(id);
            if (!(target instanceof String option) || !optionIds.contains(option)) {
                throw new InvalidItemException("Élément " + id + " : indiquez l'option attendue.");
            }
            if (!reuse && !used.add(option)) {
                throw new InvalidItemException("L'option " + option + " est attendue deux fois alors que la réutilisation est désactivée.");
            }
            pairs.put(id, option);
        }
        if (rawPairs.size() != prompts.size()) throw new InvalidItemException("« pairs » contient un élément inconnu.");

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("prompts", prompts);
        normalized.put("options", options);
        normalized.put("reuse", reuse);
        normalized.put("pairs", pairs);
        return normalized;
    }

    @Override
    public Map<String, Object> learnerView(Map<String, Object> payload) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> o : list(payload, "options")) options.add(PayloadReader.item("id", o.get("id"), "text", o.get("text")));
        Collections.shuffle(options, random);
        List<Map<String, Object>> prompts = new ArrayList<>();
        for (Map<String, Object> p : list(payload, "prompts")) prompts.add(PayloadReader.item("id", p.get("id"), "text", p.get("text")));
        return PayloadReader.item("prompts", prompts, "options", options, "reuse", Boolean.TRUE.equals(payload.get("reuse")));
    }

    @Override
    public Map<String, Object> normalizeAnswer(Map<String, Object> payload, Map<String, Object> answer) {
        if (answer == null || answer.isEmpty()) return null;
        if (!(answer.get("pairs") instanceof Map<?, ?> given)) throw new InvalidItemException("Réponse invalide : « pairs » attendu (objet).");
        Set<String> prompts = new HashSet<>();
        for (Map<String, Object> p : list(payload, "prompts")) prompts.add((String) p.get("id"));
        Set<String> options = new HashSet<>();
        for (Map<String, Object> o : list(payload, "options")) options.add((String) o.get("id"));
        boolean reuse = Boolean.TRUE.equals(payload.get("reuse"));

        Map<String, Object> clean = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Map.Entry<?, ?> e : given.entrySet()) {
            if (!(e.getKey() instanceof String promptId) || !prompts.contains(promptId)) throw new InvalidItemException("Réponse invalide : élément inconnu.");
            if (e.getValue() == null || "".equals(e.getValue())) continue;       // case vidée = pas de réponse pour cet élément
            if (!(e.getValue() instanceof String optionId) || !options.contains(optionId)) throw new InvalidItemException("Réponse invalide : option inconnue.");
            if (!reuse && !used.add(optionId)) throw new InvalidItemException("Réponse invalide : une option ne se place qu'une fois.");
            clean.put(promptId, optionId);
        }
        return clean.isEmpty() ? null : Map.of("pairs", clean);
    }

    @Override
    @SuppressWarnings("unchecked")
    public double score(Map<String, Object> payload, Map<String, Object> answer) {
        Map<String, Object> normalized = answer == null ? null : normalizeAnswer(payload, answer);
        if (normalized == null) return 0;
        Map<String, Object> given = (Map<String, Object>) normalized.get("pairs");
        Map<String, Object> expected = (Map<String, Object>) payload.get("pairs");
        int right = 0;
        for (Map.Entry<String, Object> e : expected.entrySet()) {
            if (e.getValue().equals(given.get(e.getKey()))) right++;
        }
        return (double) right / expected.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> correctAnswer(Map<String, Object> payload) {
        return Map.of("pairs", new LinkedHashMap<>((Map<String, Object>) payload.get("pairs")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> payload, String key) {
        return (List<Map<String, Object>>) payload.get(key);
    }
}
