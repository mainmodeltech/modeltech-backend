package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Choix multiples (« Choose two ») : 3 à 8 choix, au moins 2 bonnes réponses et au moins un mauvais choix. */
@Component
class MultipleChoiceStrategy extends AbstractChoiceStrategy {

    @Override
    public ItemType type() {
        return ItemType.MULTIPLE_CHOICE;
    }

    @Override
    protected int minChoices() {
        return 3;
    }

    @Override
    protected int maxChoices() {
        return 8;
    }

    @Override
    protected void checkCorrectCount(int correct, int total) {
        if (correct < 2) throw new InvalidItemException("Une question à choix multiples a au moins 2 bonnes réponses (reçu : " + correct + ").");
        if (correct >= total) throw new InvalidItemException("Au moins un choix doit être faux.");
    }

    /** Le nombre de réponses à sélectionner est annoncé au candidat (« Choose two »). */
    @Override
    protected void extend(Map<String, Object> normalized, int correct) {
        normalized.put("selectCount", correct);
    }
}
