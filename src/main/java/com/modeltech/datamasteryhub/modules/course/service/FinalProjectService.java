package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Projet final : consigne (back-office), dépôt des fichiers (apprenant), correction (formateur). */
public interface FinalProjectService {

    // ── Apprenant ───────────────────────────────────────────────────

    /** Null si la formation n'a pas de projet final. */
    EvaluationPayloads.ProjectOverview getLearnerProject(String learnerEmail, UUID formationId);

    EvaluationPayloads.ProjectOverview uploadFile(String learnerEmail, UUID formationId, MultipartFile file);

    EvaluationPayloads.ProjectOverview deleteFile(String learnerEmail, UUID formationId, UUID fileId);

    // ── Back-office : consigne ──────────────────────────────────────

    /** Tous les champs sont nuls tant qu'aucune consigne n'est enregistrée. */
    EvaluationPayloads.ProjectConfig getConfig(UUID formationId, String actorEmail, Collection<String> actorRoles);

    EvaluationPayloads.ProjectConfig saveConfig(UUID formationId, EvaluationPayloads.ProjectConfig config,
                                                String actorEmail, Collection<String> actorRoles);

    /** Retire le projet final de la formation (les rendus existants sont conservés). */
    void deleteConfig(UUID formationId, String actorEmail, Collection<String> actorRoles);

    // ── Back-office : correction ────────────────────────────────────

    /** Valide le rendu ou demande des corrections, avec un message pour l'apprenant. */
    EvaluationPayloads.ProjectOverview review(UUID sessionId, UUID learnerId, EvaluationPayloads.ProjectReview review,
                                              String actorEmail, Collection<String> actorRoles);

    /** Fichiers rendus par l'apprenant, avec des liens de téléchargement temporaires. */
    List<EvaluationPayloads.ProjectFileLink> files(UUID sessionId, UUID learnerId, String actorEmail, Collection<String> actorRoles);
}
