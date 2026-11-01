package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.springframework.stereotype.Component;

/** Choix unique : 2 à 6 choix, exactement une bonne réponse. */
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
}
