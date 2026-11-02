package com.modeltech.datamasteryhub.modules.simulator.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Squelette commun aux questions à choix (Template Method) : lecture des choix, unicité des identifiants (A à H),
 * décompte des bonnes réponses, vue apprenant, réponse {@code {"selected":["A","C"]}} ; les sous-classes imposent le
 * nombre de choix et de bonnes réponses, la taille maximale d'une sélection et la règle de notation.
 */
abstract class AbstractChoiceStrategy implements ItemTypeStrategy {

    private static final String CHOICE_ID = "^[A-H]$";

    protected abstract int minChoices();

    protected abstract int maxChoices();

    /** Contrôle le nombre de bonnes réponses ; lève {@link InvalidItemException} sinon. */
    protected abstract void checkCorrectCount(int correct, int total);

    /** Nombre maximal de choix qu'une réponse peut contenir. */
    protected abstract int maxSelectable(int correctCount);

    /** Note de 0 à 1 d'une sélection valide. */
    protected abstract double scoreSelection(Set<String> correct, Set<String> selected);

    /** Champs supplémentaires du payload normalisé (ex. nombre de réponses à sélectionner). */
    protected void extend(Map<String, Object> normalized, int correct) {}

    @Override
    public Map<String, Object> validateAndNormalize(Map<String, Object> payload) {
        List<Map<String, Object>> raw = PayloadReader.objects(payload, "choices", minChoices(), maxChoices());
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        int correct = 0;
        for (Map<String, Object> c : raw) {
            String id = PayloadReader.id(c, CHOICE_ID, "Choix");
            if (!ids.add(id)) throw new InvalidItemException("Choix " + id + " : identifiant en double.");
            String text = PayloadReader.text(c, "text", "Choix " + id);
            boolean isCorrect = PayloadReader.flag(c, "correct", "Choix " + id);
            if (isCorrect) correct++;
            choices.add(PayloadReader.item("id", id, "text", text, "correct", isCorrect));
        }
        checkCorrectCount(correct, choices.size());
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("choices", choices);
        extend(normalized, correct);
        return normalized;
    }

    @Override
    public Map<String, Object> learnerView(Map<String, Object> payload) {
        List<Map<String, Object>> shown = new ArrayList<>();
        for (Map<String, Object> c : choices(payload)) shown.add(PayloadReader.item("id", c.get("id"), "text", c.get("text")));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("choices", shown);
        view.put("selectCount", maxSelectable(correctIds(payload).size()));
        return view;
    }

    @Override
    public Map<String, Object> normalizeAnswer(Map<String, Object> payload, Map<String, Object> answer) {
        if (answer == null || answer.isEmpty()) return null;
        if (!(answer.get("selected") instanceof List<?> list)) throw new InvalidItemException("Réponse invalide : « selected » attendu (liste).");
        Set<String> known = new HashSet<>();
        for (Map<String, Object> c : choices(payload)) known.add((String) c.get("id"));
        Set<String> selected = new LinkedHashSet<>();
        for (Object o : list) {
            if (!(o instanceof String id) || !known.contains(id)) throw new InvalidItemException("Réponse invalide : choix inconnu.");
            selected.add(id);
        }
        if (selected.size() > maxSelectable(correctIds(payload).size())) {
            throw new InvalidItemException("Trop de choix sélectionnés.");
        }
        if (selected.isEmpty()) return null;
        return Map.of("selected", new ArrayList<>(selected));
    }

    @Override
    public double score(Map<String, Object> payload, Map<String, Object> answer) {
        Map<String, Object> normalized = answer == null ? null : normalizeAnswer(payload, answer);
        if (normalized == null) return 0;
        Set<String> selected = new LinkedHashSet<>(selectedOf(normalized));
        return scoreSelection(correctIds(payload), selected);
    }

    @Override
    public Map<String, Object> correctAnswer(Map<String, Object> payload) {
        return Map.of("selected", new ArrayList<>(correctIds(payload)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> choices(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("choices");
    }

    @SuppressWarnings("unchecked")
    private static List<String> selectedOf(Map<String, Object> answer) {
        return (List<String>) answer.get("selected");
    }

    private static Set<String> correctIds(Map<String, Object> payload) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> c : choices(payload)) if (Boolean.TRUE.equals(c.get("correct"))) ids.add((String) c.get("id"));
        return ids;
    }
}
