package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Filtre de la liste des candidatures (back-office) : étape du parcours, recherche libre, formation, session.
 * Tout est filtré côté serveur : la liste reste fluide avec des centaines de candidatures.
 */
public record RegistrationFilter(Stage stage, RegistrationStatus status, String q, UUID bootcampId, UUID sessionId) {

    /** Étapes du parcours, dans l'ordre où l'équipe les traite. */
    public enum Stage {
        /** À examiner. */
        NEW(EnumSet.of(RegistrationStatus.PENDING)),
        /** Acceptée, lien de paiement envoyé. */
        AWAITING_PAYMENT(EnumSet.of(RegistrationStatus.PAYMENT_PENDING)),
        /** Paiement déclaré, à vérifier. */
        TO_CONFIRM(EnumSet.of(RegistrationStatus.PAYMENT_TO_CONFIRM)),
        /** Inscrit (confirmé ou terminé). */
        ENROLLED(EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.COMPLETED)),
        /** Refusée ou annulée. */
        ARCHIVED(EnumSet.of(RegistrationStatus.REJECTED, RegistrationStatus.CANCELLED));

        private final Set<RegistrationStatus> statuses;

        Stage(Set<RegistrationStatus> statuses) { this.statuses = statuses; }

        public Set<RegistrationStatus> statuses() { return statuses; }
    }

    public static RegistrationFilter none() {
        return new RegistrationFilter(null, null, null, null, null);
    }

    /** Même filtre sans l'étape (compteurs par étape). */
    public RegistrationFilter withoutStage() {
        return new RegistrationFilter(null, null, q, bootcampId, sessionId);
    }

    public Specification<Registration> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.isFalse(root.get("isDeleted")));
            if (stage != null) all.add(root.get("status").in(stage.statuses()));
            if (status != null) all.add(cb.equal(root.get("status"), status));
            if (bootcampId != null) all.add(cb.equal(root.get("bootcamp").get("id"), bootcampId));
            if (sessionId != null) all.add(cb.equal(root.get("session").get("id"), sessionId));
            if (q != null && !q.isBlank()) {
                for (String word : q.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                    String like = "%" + escape(word) + "%";
                    all.add(cb.or(
                            cb.like(cb.lower(root.get("firstName")), like, '\\'),
                            cb.like(cb.lower(root.get("lastName")), like, '\\'),
                            cb.like(cb.lower(root.get("email")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(root.<String>get("company"), "")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(root.<String>get("phone"), "")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(root.<String>get("bootcampTitle"), "")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(root.<String>get("sessionName"), "")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(root.<String>get("promoCodeUsed"), "")), like, '\\')));
                }
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
