package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vue apprenant sans corrigé, validation des réponses et notation par type de question (sans Spring). */
class ItemScoringTest {

    private final ItemTypeRegistry registry = new ItemTypeRegistry(List.of(
            new SingleChoiceStrategy(), new MultipleChoiceStrategy(), new YesNoSeriesStrategy(), new MatchingStrategy(), new ShortAnswerStrategy()));

    private Map<String, Object> single() {
        return registry.validateAndNormalize(ItemType.SINGLE_CHOICE, Map.of("choices", List.of(
                Map.of("id", "A", "text", "One", "correct", true), Map.of("id", "B", "text", "Two", "correct", false),
                Map.of("id", "C", "text", "Three", "correct", false))));
    }

    private Map<String, Object> multiple() {
        return registry.validateAndNormalize(ItemType.MULTIPLE_CHOICE, Map.of("choices", List.of(
                Map.of("id", "A", "text", "a", "correct", true), Map.of("id", "B", "text", "b", "correct", true),
                Map.of("id", "C", "text", "c", "correct", false), Map.of("id", "D", "text", "d", "correct", false))));
    }

    private Map<String, Object> yesNo() {
        return registry.validateAndNormalize(ItemType.YES_NO_SERIES, Map.of("statements", List.of(
                Map.of("id", "S1", "text", "first", "answer", true), Map.of("id", "S2", "text", "second", "answer", false),
                Map.of("id", "S3", "text", "third", "answer", true), Map.of("id", "S4", "text", "fourth", "answer", false))));
    }

    @Test
    void theLearnerViewNeverCarriesAnyAnswerKey() {
        for (var e : Map.of(ItemType.SINGLE_CHOICE, single(), ItemType.MULTIPLE_CHOICE, multiple(), ItemType.YES_NO_SERIES, yesNo()).entrySet()) {
            String json = registry.learnerView(e.getKey(), e.getValue()).toString();
            assertThat(json).as(e.getKey().name()).doesNotContain("correct").doesNotContain("answer=").doesNotContain("explanation");
        }
        assertThat(registry.learnerView(ItemType.MULTIPLE_CHOICE, multiple()).get("selectCount")).isEqualTo(2);
        assertThat(registry.learnerView(ItemType.SINGLE_CHOICE, single()).get("selectCount")).isEqualTo(1);
    }

    @Test
    void singleChoice_isAllOrNothing_andRefusesUnknownOrExtraChoices() {
        assertThat(registry.score(ItemType.SINGLE_CHOICE, single(), Map.of("selected", List.of("A")))).isEqualTo(1.0);
        assertThat(registry.score(ItemType.SINGLE_CHOICE, single(), Map.of("selected", List.of("B")))).isZero();
        assertThat(registry.score(ItemType.SINGLE_CHOICE, single(), null)).isZero();
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.SINGLE_CHOICE, single(), Map.of("selected", List.of("Z"))))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.SINGLE_CHOICE, single(), Map.of("selected", List.of("A", "B"))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("Trop");
        assertThat(registry.normalizeAnswer(ItemType.SINGLE_CHOICE, single(), Map.of("selected", List.of()))).isNull();
        assertThat(registry.correctAnswer(ItemType.SINGLE_CHOICE, single())).isEqualTo(Map.of("selected", List.of("A")));
    }

    @Test
    void multipleChoice_givesPartialCredit_butWrongPicksCancelRightOnes() {
        assertThat(registry.score(ItemType.MULTIPLE_CHOICE, multiple(), Map.of("selected", List.of("A", "B")))).isEqualTo(1.0);
        assertThat(registry.score(ItemType.MULTIPLE_CHOICE, multiple(), Map.of("selected", List.of("A")))).isEqualTo(0.5);
        assertThat(registry.score(ItemType.MULTIPLE_CHOICE, multiple(), Map.of("selected", List.of("A", "C")))).isZero();
        assertThat(registry.score(ItemType.MULTIPLE_CHOICE, multiple(), Map.of("selected", List.of("C", "D")))).isZero();
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.MULTIPLE_CHOICE, multiple(), Map.of("selected", List.of("A", "B", "C"))))
                .isInstanceOf(InvalidItemException.class);
    }

    @Test
    void yesNoSeries_scoresTheShareOfStatementsJudgedCorrectly() {
        assertThat(registry.score(ItemType.YES_NO_SERIES, yesNo(), Map.of("answers", Map.of("S1", true, "S2", false, "S3", true, "S4", false)))).isEqualTo(1.0);
        assertThat(registry.score(ItemType.YES_NO_SERIES, yesNo(), Map.of("answers", Map.of("S1", true, "S2", true)))).isEqualTo(0.25);
        assertThat(registry.score(ItemType.YES_NO_SERIES, yesNo(), null)).isZero();
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.YES_NO_SERIES, yesNo(), Map.of("answers", Map.of("S9", true))))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.YES_NO_SERIES, yesNo(), Map.of("answers", Map.of("S1", "yes"))))
                .isInstanceOf(InvalidItemException.class);
    }
}
