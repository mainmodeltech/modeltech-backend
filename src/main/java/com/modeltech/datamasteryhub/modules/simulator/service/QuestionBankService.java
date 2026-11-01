package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.simulator.dto.SimulatorPayloads.*;
import com.modeltech.datamasteryhub.modules.simulator.entity.*;
import com.modeltech.datamasteryhub.modules.simulator.repository.*;
import com.modeltech.datamasteryhub.modules.simulator.strategy.ItemTypeRegistry;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Banque de questions du simulateur : rédaction, relecture, publication et retrait, avec versionnage immuable.
 * L'identité de l'acteur est toujours celle de la session (jamais un paramètre) ; les décisions sensibles
 * (approbation, publication, retrait, import) sont consignées dans le journal d'audit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionBankService {

    /** Certification servie par ce module au lancement ; le moteur est générique, le code viendra de l'URL au besoin. */
    public static final String CERTIFICATION = "PL-300";
    private static final int PREVIEW = 140;
    private static final int MAX_PAGE_SIZE = 100;

    private final SimCertificationRepository certifications;
    private final SimBlueprintRepository blueprints;
    private final SimBlueprintSkillRepository skills;
    private final SimItemRepository items;
    private final SimItemVersionRepository versions;
    private final ItemTypeRegistry typeRegistry;
    private final AuditService audit;

    // ── Blueprint et couverture ─────────────────────────────────────

    public BlueprintView blueprint() {
        SimBlueprint b = activeBlueprint();
        return new BlueprintView(b.getId(), b.getCertification().getCode(), b.getVersionDate(), b.getLabel(), b.getDurationMinutes(),
                b.getQuestionsMin(), b.getQuestionsMax(), b.getPassScore(),
                b.getDomains().stream().map(d -> new DomainView(d.getCode(), d.getName(), d.getWeightMin(), d.getWeightMax(),
                        d.getSkills().stream().map(s -> new SkillView(s.getId(), s.getCode(), s.getName(), s.getTargetQuestions())).toList())).toList());
    }

    /** Avancement de la production par sous-compétence : cible, brouillons, en relecture, approuvées, publiées. */
    public List<CoverageRow> coverage() {
        Map<UUID, Map<ItemStatus, Long>> counts = new HashMap<>();
        for (Object[] row : versions.countBySkillAndStatus()) {
            counts.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(ItemStatus.class)).put((ItemStatus) row[1], (Long) row[2]);
        }
        List<CoverageRow> out = new ArrayList<>();
        for (SimBlueprintDomain d : activeBlueprint().getDomains()) {
            for (SimBlueprintSkill s : d.getSkills()) {
                Map<ItemStatus, Long> c = counts.getOrDefault(s.getId(), Map.of());
                out.add(new CoverageRow(d.getCode(), s.getCode(), s.getName(), s.getTargetQuestions(),
                        c.getOrDefault(ItemStatus.DRAFT, 0L), c.getOrDefault(ItemStatus.IN_REVIEW, 0L),
                        c.getOrDefault(ItemStatus.APPROVED, 0L), c.getOrDefault(ItemStatus.PUBLISHED, 0L)));
            }
        }
        return out;
    }

    // ── Lecture ─────────────────────────────────────────────────────

    /** Une ligne par question (sa version la plus récente), filtrable. */
    public Page<ItemSummary> search(ItemStatus status, String domainCode, String skillCode, ItemType type, String q,
                                    String authorEmail, int page, int size) {
        Specification<SimItemVersion> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Subquery<Integer> latest = query.subquery(Integer.class);
            var v2 = latest.from(SimItemVersion.class);
            latest.select(cb.max(v2.<Integer>get("versionNo"))).where(cb.equal(v2.get("item"), root.get("item")));
            p.add(cb.equal(root.get("versionNo"), latest));

            Join<?, ?> skill = root.join("item").join("skill");
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (type != null) p.add(cb.equal(root.get("type"), type));
            if (skillCode != null && !skillCode.isBlank()) p.add(cb.equal(skill.get("code"), skillCode.trim()));
            if (domainCode != null && !domainCode.isBlank()) p.add(cb.equal(skill.join("domain").get("code"), domainCode.trim()));
            if (authorEmail != null) p.add(cb.equal(cb.lower(root.get("author")), authorEmail.toLowerCase(Locale.ROOT)));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("stemEn")), like), cb.like(cb.lower(root.get("item").get("publicCode")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"));
        return versions.findAll(spec, pageable).map(this::summary);
    }

    /** Détail d'une question ; {@code versionNo} absent : la version la plus récente. */
    public ItemVersionView get(String code, Integer versionNo) {
        SimItem item = requireItem(code);
        SimItemVersion v = versionNo == null ? latest(item) : requireVersion(item, versionNo);
        return view(v);
    }

    // ── Rédaction ───────────────────────────────────────────────────

    @Transactional
    public ItemVersionView create(ItemRequest req, String actor) {
        return view(createDraft(req, actor));
    }

    @Transactional
    public ItemVersionView update(String code, int versionNo, ItemRequest req, String actor) {
        SimItem item = requireItem(code);
        SimItemVersion v = requireVersion(item, versionNo);
        ItemWorkflow.requireEditable(v, actor);
        if (req.skillCode() != null && !req.skillCode().isBlank() && !req.skillCode().trim().equals(item.getSkill().getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La compétence d'une question ne change pas : créez une nouvelle question.");
        }
        applyContent(v, req);
        return view(versions.save(v));
    }

    /** Ouvre un nouveau brouillon à partir de la dernière version (corriger une question publiée sans toucher à l'ancienne). */
    @Transactional
    public ItemVersionView newVersion(String code, String actor) {
        SimItem item = requireItem(code);
        if (versions.existsByItemIdAndStatusIn(item.getId(), List.of(ItemStatus.DRAFT, ItemStatus.IN_REVIEW, ItemStatus.APPROVED))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une version est déjà en cours pour cette question : terminez-la d'abord.");
        }
        SimItemVersion last = latest(item);
        SimItemVersion v = new SimItemVersion();
        v.setItem(item);
        v.setVersionNo(versions.maxVersionNo(item.getId()) + 1);
        v.setType(last.getType());
        v.setDifficulty(last.getDifficulty());
        v.setStemEn(last.getStemEn());
        v.setPayload(new LinkedHashMap<>(last.getPayload()));
        v.setExplanationFr(last.getExplanationFr());
        v.setBlueprint(activeBlueprint());
        v.setAuthor(actor);
        v.setStatus(ItemStatus.DRAFT);
        return view(versions.save(v));
    }

    @Transactional
    public ItemVersionView submit(String code, int versionNo, String actor) {
        SimItemVersion v = requireVersion(requireItem(code), versionNo);
        ItemWorkflow.submit(v, actor);
        return view(versions.save(v));
    }

    @Transactional
    public void deleteDraft(String code, int versionNo, String actor) {
        SimItem item = requireItem(code);
        SimItemVersion v = requireVersion(item, versionNo);
        ItemWorkflow.requireEditable(v, actor);
        versions.delete(v);
        versions.flush();
        if (versions.maxVersionNo(item.getId()) == 0) items.delete(item);
    }

    // ── Relecture et publication ────────────────────────────────────

    @Transactional
    public ItemVersionView review(String code, int versionNo, ReviewRequest req, String reviewer) {
        SimItemVersion v = requireVersion(requireItem(code), versionNo);
        if (req.approve()) {
            ItemWorkflow.approve(v, reviewer, req.verified(), req.note());
            audit.record(AuditAction.SIM_ITEM_APPROVED, "SimItem", code, "Question " + code + " (v" + versionNo + ") approuvée");
        } else {
            ItemWorkflow.requestChanges(v, reviewer, req.note());
        }
        return view(versions.save(v));
    }

    @Transactional
    public ItemVersionView publish(String code, int versionNo, String publisher) {
        SimItem item = requireItem(code);
        SimItemVersion v = requireVersion(item, versionNo);
        // Contrôle d'abord (rien n'est modifié si la publication est refusée), puis la version précédente est retirée
        // et écrite en base AVANT que la nouvelle ne devienne publiée : une seule version publiée à la fois (index unique)
        ItemWorkflow.requirePublishable(v, publisher);
        versions.findByItemIdAndStatus(item.getId(), ItemStatus.PUBLISHED).ifPresent(old -> {
            ItemWorkflow.retire(old, "Remplacée par la version " + versionNo);
            versions.saveAndFlush(old);
        });
        ItemWorkflow.publish(v, publisher);
        SimItemVersion saved = versions.save(v);
        audit.record(AuditAction.SIM_ITEM_PUBLISHED, "SimItem", code, "Question " + code + " (v" + versionNo + ") publiée");
        return view(saved);
    }

    @Transactional
    public ItemVersionView retire(String code, int versionNo, String reason, String actor) {
        SimItemVersion v = requireVersion(requireItem(code), versionNo);
        ItemWorkflow.retire(v, reason);
        audit.record(AuditAction.SIM_ITEM_RETIRED, "SimItem", code, "Question " + code + " (v" + versionNo + ") retirée : " + reason.trim());
        return view(versions.save(v));
    }

    // ── Import ──────────────────────────────────────────────────────

    /**
     * Importe des brouillons en lot. Chaque question est contrôlée <b>avant</b> toute écriture : une question invalide est
     * signalée (rang et motif) sans bloquer les autres. Tous les imports passent ensuite par la relecture habituelle.
     */
    @Transactional
    public ImportResult importItems(ImportRequest req, String actor) {
        List<String> codes = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        for (int i = 0; i < req.items().size(); i++) {
            try {
                codes.add(createDraft(req.items().get(i), actor).getItem().getPublicCode());
            } catch (ResponseStatusException e) {
                errors.add(new ImportError(i, e.getReason()));
            }
        }
        if (!codes.isEmpty()) {
            audit.record(AuditAction.SIM_ITEMS_IMPORTED, "SimItem", null,
                    codes.size() + " question(s) du simulateur importée(s) en brouillon" + (errors.isEmpty() ? "" : " (" + errors.size() + " refusée(s))"));
        }
        return new ImportResult(codes.size(), codes, errors);
    }

    // ── Privé ───────────────────────────────────────────────────────

    private SimItemVersion createDraft(ItemRequest req, String actor) {
        if (req.skillCode() == null || req.skillCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La compétence mesurée est obligatoire.");
        }
        SimBlueprint blueprint = activeBlueprint();
        SimBlueprintSkill skill = skills.findByCodeAndDomainBlueprintId(req.skillCode().trim(), blueprint.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Compétence inconnue : " + req.skillCode()));
        Map<String, Object> payload = typeRegistry.validateAndNormalize(req.type(), req.payload());

        SimItem item = new SimItem();
        item.setCertification(blueprint.getCertification());
        item.setSkill(skill);
        item.setCreatedBy(actor);
        item.setPublicCode(nextCode(blueprint.getCertification(), skill.getDomain()));
        items.save(item);

        SimItemVersion v = new SimItemVersion();
        v.setItem(item);
        v.setVersionNo(1);
        v.setBlueprint(blueprint);
        v.setAuthor(actor);
        v.setStatus(ItemStatus.DRAFT);
        v.setType(req.type());
        v.setDifficulty((short) req.difficulty());
        v.setStemEn(req.stemEn().trim());
        v.setPayload(payload);
        v.setExplanationFr(blankToNull(req.explanationFr()));
        return versions.save(v);
    }

    private void applyContent(SimItemVersion v, ItemRequest req) {
        v.setType(req.type());
        v.setDifficulty((short) req.difficulty());
        v.setStemEn(req.stemEn().trim());
        v.setPayload(typeRegistry.validateAndNormalize(req.type(), req.payload()));
        v.setExplanationFr(blankToNull(req.explanationFr()));
        v.setUpdatedAt(java.time.LocalDateTime.now());
    }

    private String nextCode(SimCertification certification, SimBlueprintDomain domain) {
        String prefix = certification.getCode().replace("-", "") + "-" + domain.getCode() + "-";
        return prefix + String.format("%03d", items.maxSequence(prefix) + 1);
    }

    private SimBlueprint activeBlueprint() {
        return blueprints.findFirstByCertificationCodeAndActiveTrueOrderByVersionDateDesc(CERTIFICATION)
                .orElseThrow(() -> new ResourceNotFoundException("Blueprint", "certification", CERTIFICATION));
    }

    private SimItem requireItem(String code) {
        return items.findByPublicCode(code).orElseThrow(() -> new ResourceNotFoundException("Question", "code", code));
    }

    private SimItemVersion requireVersion(SimItem item, int versionNo) {
        return versions.findByItemIdAndVersionNo(item.getId(), versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Version", "numéro", versionNo));
    }

    private SimItemVersion latest(SimItem item) {
        return versions.findByItemIdAndVersionNo(item.getId(), versions.maxVersionNo(item.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Question", "code", item.getPublicCode()));
    }

    private ItemSummary summary(SimItemVersion v) {
        String stem = v.getStemEn().length() > PREVIEW ? v.getStemEn().substring(0, PREVIEW) + "…" : v.getStemEn();
        return new ItemSummary(v.getItem().getPublicCode(), v.getVersionNo(), v.getItem().getSkill().getDomain().getCode(),
                v.getItem().getSkill().getCode(), v.getType(), v.getDifficulty(), v.getStatus(), v.getAuthor(), stem,
                v.getUpdatedAt() != null ? v.getUpdatedAt() : v.getCreatedAt());
    }

    private ItemVersionView view(SimItemVersion v) {
        SimItem item = v.getItem();
        List<Integer> all = versions.versionNumbers(item.getId());
        return new ItemVersionView(item.getPublicCode(), v.getVersionNo(), item.getSkill().getDomain().getCode(), item.getSkill().getCode(),
                v.getType(), v.getDifficulty(), v.getStemEn(), v.getPayload(), v.getExplanationFr(), v.getStatus(),
                v.getBlueprint().getLabel(), v.getAuthor(), v.getReviewer(), v.getReviewNote(), v.getVerifiedBy(), v.getVerifiedAt(),
                v.getPublisher(), v.getCreatedAt(), v.getPublishedAt(), v.getRetiredAt(), v.getRetireReason(), all);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
