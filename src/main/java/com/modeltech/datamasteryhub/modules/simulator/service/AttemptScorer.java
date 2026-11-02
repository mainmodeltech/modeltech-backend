package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimAttemptAnswer;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimBlueprint;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimBlueprintDomain;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimExamFormItem;
import com.modeltech.datamasteryhub.modules.simulator.strategy.ItemTypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Notation d'une tentative : squelette commun (noter chaque question avec la stratégie de son type, agréger par domaine
 * et par compétence, estimer le score), sans état. Le score sur 1 000 est <b>indicatif</b> : le barème exact de
 * Microsoft n'est pas public ; on le calcule linéairement (70 % ≈ 700).
 */
@Component
@RequiredArgsConstructor
public class AttemptScorer {

    public record Bucket(String code, String name, int questions, double points) {
        public int percent() {
            return questions == 0 ? 0 : (int) Math.round(points * 100.0 / questions);
        }
    }

    public record Scoring(double percent, int scaled, List<Bucket> domains, List<Bucket> skills) {}

    private final ItemTypeRegistry registry;

    /** Note d'une question (0 à 1) pour la réponse donnée (ou absente). */
    public double scoreItem(SimExamFormItem item, Map<String, Object> answer) {
        var v = item.getItemVersion();
        return registry.score(v.getType(), v.getPayload(), answer);
    }

    public Scoring score(SimBlueprint blueprint, List<SimExamFormItem> items, List<SimAttemptAnswer> answers) {
        Map<UUID, Map<String, Object>> byItem = new HashMap<>();
        answers.forEach(a -> byItem.put(a.getFormItem().getId(), a.getAnswer()));

        Map<String, double[]> domains = new LinkedHashMap<>();   // code -> {questions, points}
        Map<String, double[]> skills = new LinkedHashMap<>();
        double total = 0;
        for (SimExamFormItem item : items) {
            double s = scoreItem(item, byItem.get(item.getId()));
            total += s;
            add(domains, item.getDomainCode(), s);
            add(skills, item.getSkillCode(), s);
        }
        double percent = items.isEmpty() ? 0 : Math.round(total * 10000.0 / items.size()) / 100.0;
        int scaled = (int) Math.round(percent * 10);

        Map<String, String> names = new HashMap<>();
        for (SimBlueprintDomain d : blueprint.getDomains()) {
            names.put(d.getCode(), d.getName());
            d.getSkills().forEach(sk -> names.put(sk.getCode(), sk.getName()));
        }
        return new Scoring(percent, scaled, buckets(domains, names), buckets(skills, names));
    }

    private static void add(Map<String, double[]> map, String key, double points) {
        double[] cell = map.computeIfAbsent(key, k -> new double[2]);
        cell[0] += 1;
        cell[1] += points;
    }

    private static List<Bucket> buckets(Map<String, double[]> map, Map<String, String> names) {
        List<Bucket> out = new ArrayList<>();
        map.forEach((code, cell) -> out.add(new Bucket(code, names.getOrDefault(code, code), (int) cell[0], cell[1])));
        return out;
    }
}
