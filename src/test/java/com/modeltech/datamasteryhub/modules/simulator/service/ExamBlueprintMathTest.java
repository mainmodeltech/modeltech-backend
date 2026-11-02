package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.simulator.service.ExamBlueprintMath.DomainRule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Répartition d'un examen selon le blueprint (sans Spring). */
class ExamBlueprintMathTest {

    private static final List<DomainRule> PL300 = List.of(
            new DomainRule("PRE", 25, 30), new DomainRule("MOD", 25, 30), new DomainRule("VIS", 25, 30), new DomainRule("SEC", 15, 20));

    @Test
    void fiftyQuestions_followTheMidpointWeights() {
        Map<String, Integer> alloc = ExamBlueprintMath.allocateDomains(PL300, 50);
        assertThat(alloc).containsExactly(Map.entry("PRE", 14), Map.entry("MOD", 14), Map.entry("VIS", 14), Map.entry("SEC", 8));
    }

    @Test
    void everyRealisticExamSizeRespectsTheTotalAndTheWeightBounds() {
        for (int total = 40; total <= 60; total++) {
            Map<String, Integer> alloc = ExamBlueprintMath.allocateDomains(PL300, total);
            assertThat(alloc.values().stream().mapToInt(Integer::intValue).sum()).as("total " + total).isEqualTo(total);
            for (DomainRule r : PL300) {
                double pct = alloc.get(r.code()) * 100.0 / total;
                assertThat(pct).as(r.code() + " pour " + total + " questions").isBetween(r.weightMin() - 0.001, r.weightMax() + 0.001);
            }
        }
    }

    @Test
    void aTinyExamStillSumsToTheTotal() {
        for (int total = 1; total <= 12; total++) {
            assertThat(ExamBlueprintMath.allocateDomains(PL300, total).values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
        }
    }

    @Test
    void rejectsAnEmptyBlueprintOrASizeOfZero() {
        assertThatThrownBy(() -> ExamBlueprintMath.allocateDomains(List.of(), 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExamBlueprintMath.allocateDomains(PL300, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proportionalAllocation_usesTheLargestRemainders_andFallsBackToEqualShares() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("A", 30);
        weights.put("B", 30);
        weights.put("C", 60);
        Map<String, Integer> alloc = ExamBlueprintMath.allocateProportional(weights, 14);
        assertThat(alloc.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(14);
        assertThat(alloc.get("C")).isEqualTo(7);

        Map<String, Integer> zeros = new LinkedHashMap<>();
        zeros.put("A", 0);
        zeros.put("B", 0);
        assertThat(ExamBlueprintMath.allocateProportional(zeros, 5).values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(5);
    }
}
