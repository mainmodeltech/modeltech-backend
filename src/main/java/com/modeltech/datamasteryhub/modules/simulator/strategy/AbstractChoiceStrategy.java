package com.modeltech.datamasteryhub.modules.simulator.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Squelette commun aux questions à choix (Template Method) : lecture des choix, unicité des identifiants (A à H),
 * décompte des bonnes réponses ; les sous-classes imposent le nombre de choix et de bonnes réponses attendus.
 */
abstract class AbstractChoiceStrategy implements ItemTypeStrategy {

    private static final String CHOICE_ID = "^[A-H]$";

    protected abstract int minChoices();

    protected abstract int maxChoices();

    /** Contrôle le nombre de bonnes réponses ; lève {@link InvalidItemException} sinon. */
    protected abstract void checkCorrectCount(int correct, int total);

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
}
