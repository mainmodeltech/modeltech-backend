package com.modeltech.datamasteryhub.modules.simulator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Répartition d'un examen selon le blueprint, sans aucune dépendance technique (testable seule).
 * Le nombre de questions de chaque domaine respecte les bornes de pondération du guide lorsque c'est possible
 * (ex. 50 questions : 14 / 14 / 14 / 8 pour 25–30 % ×3 et 15–20 %), le reste suit les poids médians.
 */
public final class ExamBlueprintMath {

    private ExamBlueprintMath() {}

    public record DomainRule(String code, int weightMin, int weightMax) {}

    public static Map<String, Integer> allocateDomains(List<DomainRule> rules, int total) {
        if (rules.isEmpty() || total <= 0) throw new IllegalArgumentException("Blueprint ou taille d'examen invalide");
        int n = rules.size();
        int[] lower = new int[n];
        int[] upper = new int[n];
        double[] target = new double[n];
        int sumLower = 0;
        int sumUpper = 0;
        for (int i = 0; i < n; i++) {
            DomainRule r = rules.get(i);
            lower[i] = (int) Math.ceil(r.weightMin() * total / 100.0);
            upper[i] = (int) Math.floor(r.weightMax() * total / 100.0);
            target[i] = (r.weightMin() + r.weightMax()) / 2.0 * total / 100.0;
            sumLower += lower[i];
            sumUpper += upper[i];
        }
        // Bornes irréalisables (très petit examen) : on se rabat sur les poids médians, sans bornes
        boolean bounded = sumLower <= total && sumUpper >= total;
        int[] count = new int[n];
        int remaining = total;
        if (bounded) {
            for (int i = 0; i < n; i++) count[i] = lower[i];
            remaining -= sumLower;
        } else {
            for (int i = 0; i < n; i++) upper[i] = total;
        }
        while (remaining > 0) {
            int best = -1;
            double bestGap = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (count[i] >= upper[i]) continue;
                double gap = target[i] - count[i];
                if (gap > bestGap) {
                    bestGap = gap;
                    best = i;
                }
            }
            count[best]++;
            remaining--;
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) out.put(rules.get(i).code(), count[i]);
        return out;
    }

    /** Répartition proportionnelle (plus forts restes) ; poids nuls ou absents : répartition égale. */
    public static Map<String, Integer> allocateProportional(Map<String, Integer> weights, int total) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (weights.isEmpty() || total <= 0) {
            weights.keySet().forEach(k -> out.put(k, 0));
            return out;
        }
        long sum = weights.values().stream().mapToLong(Integer::longValue).sum();
        Map<String, Double> exact = new LinkedHashMap<>();
        weights.forEach((k, w) -> exact.put(k, sum == 0 ? (double) total / weights.size() : (double) w * total / sum));
        int assigned = 0;
        for (var e : exact.entrySet()) {
            int base = (int) Math.floor(e.getValue());
            out.put(e.getKey(), base);
            assigned += base;
        }
        List<Map.Entry<String, Double>> byRemainder = new ArrayList<>(exact.entrySet());
        byRemainder.sort((a, b) -> Double.compare(b.getValue() - Math.floor(b.getValue()), a.getValue() - Math.floor(a.getValue())));
        for (int i = 0; assigned < total; i = (i + 1) % byRemainder.size()) {
            out.merge(byRemainder.get(i).getKey(), 1, Integer::sum);
            assigned++;
        }
        return out;
    }
}
