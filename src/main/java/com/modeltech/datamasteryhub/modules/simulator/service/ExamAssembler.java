package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimBlueprint;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimBlueprintDomain;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimBlueprintSkill;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimItemVersion;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimAttemptRepository;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimItemVersionRepository;
import com.modeltech.datamasteryhub.modules.simulator.service.ExamBlueprintMath.DomainRule;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.*;

/**
 * Compose un examen à partir des questions <b>publiées</b> (Factory) : un examen chronométré respecte la répartition du
 * blueprint par domaine puis par compétence ; une session d'étude pioche dans le domaine ou la compétence demandés.
 * Les questions que l'apprenant n'a jamais vues passent en premier ; l'ordre final est mélangé.
 */
@Component
@RequiredArgsConstructor
public class ExamAssembler {

    /** Une question retenue, avec le domaine et la compétence qu'elle mesure. */
    public record Picked(SimItemVersion version, String domainCode, String skillCode) {}

    private final SimItemVersionRepository versions;
    private final SimAttemptRepository attempts;
    private final Random random = new SecureRandom();

    /** Examen complet : {@code total} questions réparties selon le blueprint. */
    public List<Picked> assembleExam(SimBlueprint blueprint, int total, UUID learnerId) {
        Map<UUID, Long> seen = seenBy(learnerId);
        List<DomainRule> rules = blueprint.getDomains().stream().map(d -> new DomainRule(d.getCode(), d.getWeightMin(), d.getWeightMax())).toList();
        Map<String, Integer> perDomain = ExamBlueprintMath.allocateDomains(rules, total);

        List<Picked> chosen = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        for (SimBlueprintDomain domain : blueprint.getDomains()) {
            int needed = perDomain.get(domain.getCode());
            Map<String, Integer> weights = new LinkedHashMap<>();
            domain.getSkills().forEach(s -> weights.put(s.getCode(), s.getTargetQuestions()));
            Map<String, Integer> perSkill = ExamBlueprintMath.allocateProportional(weights, needed);

            List<Picked> inDomain = new ArrayList<>();
            List<Picked> leftovers = new ArrayList<>();
            for (SimBlueprintSkill skill : domain.getSkills()) {
                List<Picked> pool = ranked(versions.findPublishedBySkillId(skill.getId()), domain.getCode(), skill.getCode(), seen);
                int take = Math.min(perSkill.get(skill.getCode()), pool.size());
                inDomain.addAll(pool.subList(0, take));
                leftovers.addAll(pool.subList(take, pool.size()));
            }
            // Une compétence manque de questions : on complète avec les autres compétences du même domaine
            if (inDomain.size() < needed) {
                leftovers.sort(Comparator.comparingLong(p -> seen.getOrDefault(p.version().getId(), 0L)));
                inDomain.addAll(leftovers.subList(0, Math.min(needed - inDomain.size(), leftovers.size())));
            }
            if (inDomain.size() < needed) shortages.add(domain.getCode() + " : " + (needed - inDomain.size()) + " question(s) publiée(s) manquante(s)");
            chosen.addAll(inDomain);
        }
        if (!shortages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pas assez de questions publiées pour composer cet examen (" + String.join(" ; ", shortages) + ").");
        }
        Collections.shuffle(chosen, random);
        return chosen;
    }

    /** Essai gratuit : les questions échantillon publiées (au plus {@code count}), toujours les mêmes quel que soit le candidat. */
    public List<Picked> assembleTrial(int count) {
        List<Picked> pool = new ArrayList<>();
        for (SimItemVersion v : versions.findPublishedTrialSamples()) {
            pool.add(new Picked(v, v.getItem().getSkill().getDomain().getCode(), v.getItem().getSkill().getCode()));
        }
        if (pool.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "L'essai gratuit n'est pas encore disponible : revenez bientôt.");
        }
        Collections.shuffle(pool, random);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }

    /** Session d'étude : jusqu'à {@code count} questions du domaine ou de la compétence demandés (ou de tout le blueprint). */
    public List<Picked> assembleStudy(SimBlueprint blueprint, String domainCode, String skillCode, int count, UUID learnerId) {
        Map<UUID, Long> seen = seenBy(learnerId);
        List<Picked> pool = new ArrayList<>();
        for (SimBlueprintDomain domain : blueprint.getDomains()) {
            if (domainCode != null && !domainCode.isBlank() && !domain.getCode().equals(domainCode.trim())) continue;
            for (SimBlueprintSkill skill : domain.getSkills()) {
                if (skillCode != null && !skillCode.isBlank() && !skill.getCode().equals(skillCode.trim())) continue;
                pool.addAll(ranked(versions.findPublishedBySkillId(skill.getId()), domain.getCode(), skill.getCode(), seen));
            }
        }
        if (pool.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucune question publiée pour cette sélection.");
        }
        pool.sort(Comparator.comparingLong(p -> seen.getOrDefault(p.version().getId(), 0L)));
        List<Picked> chosen = new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
        Collections.shuffle(chosen, random);
        return chosen;
    }

    /** Jamais vues d'abord ; à égalité, ordre aléatoire. */
    private List<Picked> ranked(List<SimItemVersion> pool, String domainCode, String skillCode, Map<UUID, Long> seen) {
        List<Picked> picked = new ArrayList<>();
        for (SimItemVersion v : pool) picked.add(new Picked(v, domainCode, skillCode));
        Collections.shuffle(picked, random);
        picked.sort(Comparator.comparingLong(p -> seen.getOrDefault(p.version().getId(), 0L)));
        return picked;
    }

    private Map<UUID, Long> seenBy(UUID learnerId) {
        Map<UUID, Long> seen = new HashMap<>();
        for (Object[] row : attempts.seenCounts(learnerId)) seen.put((UUID) row[0], (Long) row[1]);
        return seen;
    }
}
