package com.modeltech.datamasteryhub.modules.simulator.strategy;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Correspondance (glisser-déposer) et réponse libre à mots-clés : validation, notation, aucune fuite du corrigé (sans Spring). */
class AdvancedItemTypesTest {

    private final ItemTypeRegistry registry = new ItemTypeRegistry(List.of(
            new SingleChoiceStrategy(), new MultipleChoiceStrategy(), new YesNoSeriesStrategy(), new MatchingStrategy(), new ShortAnswerStrategy()));

    private static Map<String, Object> el(String id, String text) {
        return Map.of("id", id, "text", text);
    }

    private Map<String, Object> matching() {
        return registry.validateAndNormalize(ItemType.MATCHING, Map.of(
                "prompts", List.of(el("P1", "SUM"), el("P2", "CALCULATE"), el("P3", "RELATED")),
                "options", List.of(el("O1", "Adds the values of a column"), el("O2", "Changes the filter context"), el("O3", "Reads a value from a related table"), el("O4", "Sorts a table")),
                "pairs", Map.of("P1", "O1", "P2", "O2", "P3", "O3")));
    }

    private Map<String, Object> keywords(boolean partial) {
        return registry.validateAndNormalize(ItemType.SHORT_ANSWER, Map.of(
                "keywords", List.of(Map.of("id", "K1", "accept", List.of("measure", "mesure")), Map.of("id", "K2", "accept", List.of("filter context", "contexte de filtre"))),
                "partialCredit", partial));
    }

    // ── Correspondance ───────────────────────────────────────────────

    @Test
    void matching_scoresTheShareOfCorrectPairs_andIgnoresAnEmptiedSlot() {
        Map<String, Object> p = matching();
        assertThat(registry.score(ItemType.MATCHING, p, Map.of("pairs", Map.of("P1", "O1", "P2", "O2", "P3", "O3")))).isEqualTo(1.0);
        assertThat(registry.score(ItemType.MATCHING, p, Map.of("pairs", Map.of("P1", "O1", "P2", "O4")))).isEqualTo(1.0 / 3);
        assertThat(registry.score(ItemType.MATCHING, p, Map.of("pairs", Map.of("P1", "")))).isZero();
        assertThat(registry.score(ItemType.MATCHING, p, null)).isZero();
        assertThat(registry.normalizeAnswer(ItemType.MATCHING, p, Map.of("pairs", Map.of()))).isNull();
        assertThat(registry.correctAnswer(ItemType.MATCHING, p)).isEqualTo(Map.of("pairs", Map.of("P1", "O1", "P2", "O2", "P3", "O3")));
    }

    @Test
    void matching_refusesIncompleteOrContradictoryDefinitions() {
        // une option attendue manque
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.MATCHING, Map.of(
                "prompts", List.of(el("P1", "a"), el("P2", "b")), "options", List.of(el("O1", "x"), el("O2", "y")), "pairs", Map.of("P1", "O1"))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("P2");
        // même option attendue deux fois sans réutilisation
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.MATCHING, Map.of(
                "prompts", List.of(el("P1", "a"), el("P2", "b")), "options", List.of(el("O1", "x"), el("O2", "y")), "pairs", Map.of("P1", "O1", "P2", "O1"))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("deux fois");
        // moins d'options que d'éléments
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.MATCHING, Map.of(
                "prompts", List.of(el("P1", "a"), el("P2", "b"), el("P3", "c")), "options", List.of(el("O1", "x"), el("O2", "y")),
                "pairs", Map.of("P1", "O1", "P2", "O2", "P3", "O1"))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("autant d'options");
        // option inconnue, texte d'option en double
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.MATCHING, Map.of(
                "prompts", List.of(el("P1", "a"), el("P2", "b")), "options", List.of(el("O1", "x"), el("O2", "y")), "pairs", Map.of("P1", "O1", "P2", "O7"))))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.MATCHING, Map.of(
                "prompts", List.of(el("P1", "a"), el("P2", "b")), "options", List.of(el("O1", "Same"), el("O2", "same")), "pairs", Map.of("P1", "O1", "P2", "O2"))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("même texte");
    }

    @Test
    void matching_withReuse_allowsTheSameOptionTwice_andFewerOptionsThanPrompts() {
        Map<String, Object> p = registry.validateAndNormalize(ItemType.MATCHING, Map.of(
                "reuse", true, "prompts", List.of(el("P1", "a"), el("P2", "b"), el("P3", "c")), "options", List.of(el("O1", "Yes"), el("O2", "No")),
                "pairs", Map.of("P1", "O1", "P2", "O2", "P3", "O1")));
        assertThat(registry.score(ItemType.MATCHING, p, Map.of("pairs", Map.of("P1", "O1", "P2", "O2", "P3", "O1")))).isEqualTo(1.0);
    }

    @Test
    void matching_refusesAnswersThatBreakTheRules() {
        Map<String, Object> p = matching();
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.MATCHING, p, Map.of("pairs", Map.of("P9", "O1")))).isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.MATCHING, p, Map.of("pairs", Map.of("P1", "O9")))).isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.MATCHING, p, Map.of("pairs", Map.of("P1", "O1", "P2", "O1"))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("qu'une fois");
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.MATCHING, p, Map.of("pairs", "x"))).isInstanceOf(InvalidItemException.class);
    }

    @Test
    void matching_learnerViewHidesThePairs_andShufflesTheOptions() {
        Map<String, Object> p = matching();
        Map<String, Object> view = registry.learnerView(ItemType.MATCHING, p);
        assertThat(view.toString()).doesNotContain("pairs").doesNotContain("correct");
        assertThat(view).containsOnlyKeys("prompts", "options", "reuse");
        // l'ordre des options change d'un affichage à l'autre (sinon l'ordre saisi par l'auteur trahirait la solution)
        boolean shuffled = false;
        for (int i = 0; i < 40 && !shuffled; i++) {
            shuffled = !view.get("options").equals(registry.learnerView(ItemType.MATCHING, p).get("options"));
        }
        assertThat(shuffled).isTrue();
    }

    // ── Réponse libre à mots-clés ────────────────────────────────────

    @Test
    void shortAnswer_isCorrectOnlyWhenEveryKeywordIsPresent_ignoringCaseAccentsAndPunctuation() {
        Map<String, Object> p = keywords(false);
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, Map.of("text", "Use a MEASURE, because it reacts to the Filter-Context."))).isEqualTo(1.0);
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, Map.of("text", "Une mesure évalue le contexte de filtre"))).isEqualTo(1.0);
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, Map.of("text", "A measure is enough"))).isZero();            // « filter context » manque
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, Map.of("text", "filter context only"))).isZero();
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, null)).isZero();
        assertThat(registry.normalizeAnswer(ItemType.SHORT_ANSWER, p, Map.of("text", "   "))).isNull();
    }

    @Test
    void shortAnswer_recognisesAKeywordAtTheStartOfAWord_butNotInTheMiddle() {
        Map<String, Object> p = keywords(false);
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, Map.of("text", "measures and the filter contexts"))).isEqualTo(1.0);   // pluriels
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, Map.of("text", "unmeasured, nofilter context"))).isZero();               // au milieu d'un mot
        assertThat(registry.score(ItemType.SHORT_ANSWER, p, Map.of("text", "measure, the filter and the context"))).isZero();      // les mots d'une expression restent contigus (la ponctuation compte pour un espace)
    }

    @Test
    void shortAnswer_partialCreditIsProportionalToTheKeywordsFound() {
        assertThat(registry.score(ItemType.SHORT_ANSWER, keywords(true), Map.of("text", "a measure"))).isEqualTo(0.5);
        assertThat(registry.score(ItemType.SHORT_ANSWER, keywords(true), Map.of("text", "nothing relevant"))).isZero();
        Map<String, Object> review = registry.review(ItemType.SHORT_ANSWER, keywords(true), Map.of("text", "a measure"));
        assertThat(review).containsEntry("found", List.of("K1")).containsEntry("missing", List.of("K2"));
    }

    @Test
    void shortAnswer_validatesItsDefinitionAndTheAnswerSize() {
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SHORT_ANSWER, Map.of("keywords", List.of())))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SHORT_ANSWER, Map.of("keywords", List.of(Map.of("id", "K1", "accept", List.of())))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("formulation");
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SHORT_ANSWER, Map.of("keywords", List.of(Map.of("id", "K1", "accept", List.of("***"))))))
                .isInstanceOf(InvalidItemException.class);
        assertThatThrownBy(() -> registry.validateAndNormalize(ItemType.SHORT_ANSWER,
                Map.of("keywords", List.of(Map.of("id", "K1", "accept", List.of("a"))), "maxLength", 5)))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("maxLength");

        Map<String, Object> p = registry.validateAndNormalize(ItemType.SHORT_ANSWER,
                Map.of("keywords", List.of(Map.of("id", "K1", "accept", List.of("measure", " Measure "))), "maxLength", 30));
        assertThat(((List<?>) ((Map<?, ?>) ((List<?>) p.get("keywords")).get(0)).get("accept"))).hasSize(1);   // doublons retirés
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.SHORT_ANSWER, p, Map.of("text", "x".repeat(31))))
                .isInstanceOf(InvalidItemException.class).hasMessageContaining("trop longue");
        assertThatThrownBy(() -> registry.normalizeAnswer(ItemType.SHORT_ANSWER, p, Map.of("text", 42))).isInstanceOf(InvalidItemException.class);
    }

    @Test
    void shortAnswer_learnerViewNeverCarriesTheKeywords() {
        Map<String, Object> view = registry.learnerView(ItemType.SHORT_ANSWER, keywords(false));
        assertThat(view).containsOnlyKeys("maxLength");
        assertThat(view.toString()).doesNotContain("measure").doesNotContain("keywords").doesNotContain("accept");
        assertThat(registry.correctAnswer(ItemType.SHORT_ANSWER, keywords(false))).containsKey("keywords");
    }
}
