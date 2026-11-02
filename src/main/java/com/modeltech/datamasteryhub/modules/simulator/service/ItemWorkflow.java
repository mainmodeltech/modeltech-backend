package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimItemVersion;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Règles du cycle de vie d'une version de question (machine à états + séparation des tâches), sans aucune dépendance
 * technique : testable seule. Une transition interdite lève une erreur explicite ; aucune autre voie ne modifie le statut.
 *
 * <pre>
 *  DRAFT ──submit──► IN_REVIEW ──approve──► APPROVED ──publish──► PUBLISHED ──retire──► RETIRED
 *    ▲                   │
 *    └──requestChanges───┘
 * </pre>
 *
 * Quatre yeux : l'auteur ne relit pas sa propre question ni ne la publie ; celui qui relit peut publier.
 */
public final class ItemWorkflow {

    static final int MIN_EXPLANATION = 20;

    private ItemWorkflow() {}

    public static void requireEditable(SimItemVersion v, String actor) {
        if (v.getStatus() != ItemStatus.DRAFT) {
            throw conflict("Seul un brouillon se modifie (statut : " + v.getStatus() + "). Créez une nouvelle version.");
        }
        if (!same(v.getAuthor(), actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce brouillon appartient à " + v.getAuthor() + ".");
        }
    }

    public static void submit(SimItemVersion v, String actor) {
        requireEditable(v, actor);
        if (v.getExplanationFr() == null || v.getExplanationFr().trim().length() < MIN_EXPLANATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'explication en français est obligatoire (" + MIN_EXPLANATION + " caractères au moins) avant la relecture.");
        }
        v.setStatus(ItemStatus.IN_REVIEW);
        v.setReviewer(null);
        v.setReviewNote(null);
        v.setVerifiedBy(null);
        v.setVerifiedAt(null);
        touch(v);
    }

    /** Approbation : relecteur différent de l'auteur, avec l'attestation que le contenu technique a été vérifié. */
    public static void approve(SimItemVersion v, String reviewer, boolean verified, String note) {
        requireReviewer(v, reviewer);
        if (!verified) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmez la vérification technique (DAX, M ou réglage exécuté dans Power BI) avant d'approuver.");
        }
        v.setStatus(ItemStatus.APPROVED);
        v.setReviewer(reviewer);
        v.setReviewNote(blankToNull(note));
        v.setVerifiedBy(reviewer);
        v.setVerifiedAt(LocalDateTime.now());
        touch(v);
    }

    public static void requestChanges(SimItemVersion v, String reviewer, String note) {
        requireReviewer(v, reviewer);
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez ce qu'il faut corriger.");
        }
        v.setStatus(ItemStatus.DRAFT);
        v.setReviewer(reviewer);
        v.setReviewNote(note.trim());
        touch(v);
    }

    public static void publish(SimItemVersion v, String publisher) {
        requirePublishable(v, publisher);
        LocalDateTime now = LocalDateTime.now();
        v.setStatus(ItemStatus.PUBLISHED);
        v.setPublisher(publisher);
        v.setPublishedAt(now);
        v.setUpdatedAt(now);
    }

    /** Contrôle sans rien modifier : permet de retirer la version précédente avant de publier la nouvelle. */
    public static void requirePublishable(SimItemVersion v, String publisher) {
        if (v.getStatus() != ItemStatus.APPROVED) {
            throw conflict("Seule une version approuvée se publie (statut : " + v.getStatus() + ").");
        }
        if (same(v.getAuthor(), publisher)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous avez rédigé cette question : une autre personne doit la publier.");
        }
    }

    public static void retire(SimItemVersion v, String reason) {
        if (v.getStatus() != ItemStatus.PUBLISHED) {
            throw conflict("Seule une version publiée se retire (statut : " + v.getStatus() + ").");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif du retrait est obligatoire.");
        }
        LocalDateTime now = LocalDateTime.now();
        v.setStatus(ItemStatus.RETIRED);
        v.setRetiredAt(now);
        v.setRetireReason(reason.trim());
        v.setUpdatedAt(now);
    }

    private static void requireReviewer(SimItemVersion v, String reviewer) {
        if (v.getStatus() != ItemStatus.IN_REVIEW) {
            throw conflict("Cette version n'est pas en relecture (statut : " + v.getStatus() + ").");
        }
        if (same(v.getAuthor(), reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous avez rédigé cette question : une autre personne doit la relire.");
        }
    }

    private static void touch(SimItemVersion v) {
        v.setUpdatedAt(LocalDateTime.now());
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
