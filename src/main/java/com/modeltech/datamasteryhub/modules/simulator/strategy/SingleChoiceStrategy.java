package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Choix unique : 2 à 6 choix, exactement une bonne réponse ; tout ou rien. */
@Component
class SingleChoiceStrategy extends AbstractChoiceStrategy {

    @Override
    public ItemType type() {
        return ItemType.SINGLE_CHOICE;
    }

    @Override
    protected int minChoices() {
        return 2;
    }

    @Override
    protected int maxChoices() {
        return 6;
    }

    @Override
    protected void checkCorrectCount(int correct, int total) {
        if (correct != 1) throw new InvalidItemException("Une question à choix unique a exactement une bonne réponse (reçu : " + correct + ").");
    }

    @Override
    protected int maxSelectable(int correctCount) {
        return 1;
    }

    @Override
    protected double scoreSelection(Set<String> correct, Set<String> selected) {
        return correct.equals(selected) ? 1 : 0;
    }
}
