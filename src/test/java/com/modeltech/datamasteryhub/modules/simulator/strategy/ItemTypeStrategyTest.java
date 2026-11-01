package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles de validation propres à chaque type de question (sans Spring). */
class ItemTypeStrategyTest {

    private final ItemTypeRegistry registry = new ItemTypeRegistry(List.of(
            new SingleChoiceStrategy(), new MultipleChoiceStrategy(), new YesNoSeriesStrategy()));

    private static Map<String, Object> choice(String id, boolean correct) {
        return Map.of("id", id, "text", "Choice " + id, "correct", correct);
    }

    @Test
    void singleChoice_acceptsExactlyOneCorrectAnswer_andDropsUnknownFields() {
        Map<String, Object> normalized = registry.validateAndNormalize(ItemType.SINGLE_CHOICE, Map.of(
                "choices", List.of(choice("A", true), choice("B", false), choice("C", false), choice("D", false)),
                "hack", "ignored"));
        assertThat(normalized).containsOnlyKeys("choices");
    }

    @Test
    void singleChoice_refusesZeroOrSeveralCorrectAnswers_duplicatesAndBadShapes() {
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SINGLE_CHOICE,
                Map.of("choices", List.of(choice("A", false), choice("B", false)))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("exactement une bonne réponse");
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SINGLE_CHOICE,
                Map.of("choices", List.of(choice("A", true), choice("B", true)))))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SINGLE_CHOICE,
                Map.of("choices", List.of(choice("A", true), choice("A", false)))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("double");
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SINGLE_CHOICE, Map.of("choices", "pas une liste")))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SINGLE_CHOICE, Map.of()))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SINGLE_CHOICE,
                Map.of("choices", List.of(choice("Z9", true), choice("B", false)))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("identifiant");
    }

    @Test
    void multipleChoice_announcesHowManyAnswersToSelect_andNeedsAWrongChoice() {
        Map<String, Object> normalized = registry.validateAndNormalize(ItemType.MULTIPLE_CHOICE, Map.of(
                "choices", List.of(choice("A", true), choice("B", true), choice("C", false), choice("D", false))));
        assertThat(normalized.get("selectCount")).isEqualTo(2);

        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.MULTIPLE_CHOICE,
                Map.of("choices", List.of(choice("A", true), choice("B", false), choice("C", false)))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("au moins 2");
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.MULTIPLE_CHOICE,
                Map.of("choices", List.of(choice("A", true), choice("B", true), choice("C", true)))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("faux");
    }

    @Test
    void yesNoSeries_needsTwoToSixStatementsWithABooleanAnswer() {
        Map<String, Object> ok = registry.validateAndNormalize(ItemType.YES_NO_SERIES, Map.of("statements", List.of(
                Map.of("id", "S1", "text", "First", "answer", true), Map.of("id", "S2", "text", "Second", "answer", false))));
        assertThat(ok).containsOnlyKeys("statements");

        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.YES_NO_SERIES,
                Map.of("statements", List.of(Map.of("id", "S1", "text", "Only one", "answer", true)))))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.YES_NO_SERIES, Map.of("statements", List.of(
                Map.of("id", "S1", "text", "A", "answer", "yes"), Map.of("id", "S2", "text", "B", "answer", true)))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("vrai ou faux");
    }

    @Test
    void everyItemTypeHasAStrategy() {
        for (ItemType t : ItemType.values()) {
            assertThat(registry).as(t.name()).isNotNull();
        }
        assertThatThrownBy(() -> new ItemTypeRegistry(List.of(new SingleChoiceStrategy())))
                .isInstanceOf(IllegalStateException.class);
    }
}
