package com.modeltech.datamasteryhub.modules.audit.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.audit.entity.AuditEntry;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditFlag;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Journal d'audit en lecture seule (droit {@code AUDIT_VIEW}). Aucune route d'écriture : le journal ne se modifie pas. */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Admin - Audit")
public class AdminAuditController {

    private static final int MAX_SIZE = 100;

    private final AuditRepository repository;

    public record Entry(UUID id, LocalDateTime occurredAt, String actorEmail, String actorName, String actorProfiles,
                        String action, String actionLabel, String category, String entityType, String entityId,
                        String summary, Map<String, Object> details, String flag, String flagLabel, String ip) {}

    public record ActionInfo(String code, String label, String category) {}

    public record Catalogue(List<ActionInfo> actions, List<String> categories, List<ActionInfo> flags) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<Entry>>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String flag,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        Specification<AuditEntry> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (notBlank(action)) p.add(cb.equal(root.get("action"), action.trim()));
            if (notBlank(category)) p.add(cb.equal(root.get("category"), category.trim()));
            if (notBlank(actor)) {
                String like = "%" + actor.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("actorEmail")), like), cb.like(cb.lower(cb.coalesce(root.get("actorName"), "")), like)));
            }
            if (notBlank(entityType)) p.add(cb.equal(root.get("entityType"), entityType.trim()));
            if (notBlank(entityId)) p.add(cb.equal(root.get("entityId"), entityId.trim()));
            if (notBlank(flag)) p.add("ANY".equalsIgnoreCase(flag.trim()) ? cb.isNotNull(root.get("flag")) : cb.equal(root.get("flag"), flag.trim()));
            if (notBlank(q)) p.add(cb.like(cb.lower(root.get("summary")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<Entry> result = repository.findAll(spec, pageable).map(AdminAuditController::toEntry);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " ligne(s)", result));
    }

    /** Valeurs des filtres (actions, catégories, drapeaux). */
    @GetMapping("/catalogue")
    public ResponseEntity<ApiResponse<Catalogue>> catalogue() {
        List<ActionInfo> actions = Arrays.stream(AuditAction.values())
                .map(a -> new ActionInfo(a.name(), a.label(), a.category())).toList();
        List<String> categories = actions.stream().map(ActionInfo::category).distinct().toList();
        List<ActionInfo> flags = Arrays.stream(AuditFlag.values()).map(f -> new ActionInfo(f.name(), f.label(), "")).toList();
        return ResponseEntity.ok(ApiResponse.ok("Catalogue du journal", new Catalogue(actions, categories, flags)));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static Entry toEntry(AuditEntry e) {
        String label = e.getAction();
        try {
            label = AuditAction.valueOf(e.getAction()).label();
        } catch (IllegalArgumentException ignored) {
            // action retirée du code mais encore présente dans l'historique : on affiche son code
        }
        String flagLabel = null;
        if (e.getFlag() != null) {
            try {
                flagLabel = AuditFlag.valueOf(e.getFlag()).label();
            } catch (IllegalArgumentException ignored) {
                flagLabel = e.getFlag();
            }
        }
        return new Entry(e.getId(), e.getOccurredAt(), e.getActorEmail(), e.getActorName(), e.getActorProfiles(),
                e.getAction(), label, e.getCategory(), e.getEntityType(), e.getEntityId(), e.getSummary(), e.getDetails(),
                e.getFlag(), flagLabel, e.getIp());
    }
}
