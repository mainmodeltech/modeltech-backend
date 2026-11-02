package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimAccessGrant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SimAccessGrantRepository extends JpaRepository<SimAccessGrant, UUID> {

    List<SimAccessGrant> findAllByLearnerIdOrderByCreatedAtDesc(UUID learnerId);

    boolean existsByLearnerIdAndPlanCode(UUID learnerId, com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode planCode);

    Page<SimAccessGrant> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Droits utilisables maintenant (actifs, dans leur période), ceux qui expirent le plus tôt d'abord. */
    @Query("SELECT g FROM SimAccessGrant g WHERE g.learner.id = :learnerId AND g.status = com.modeltech.datamasteryhub.modules.simulator.entity.GrantStatus.ACTIVE "
            + "AND g.startsAt <= :now AND g.expiresAt > :now ORDER BY g.expiresAt ASC")
    List<SimAccessGrant> findUsable(@Param("learnerId") UUID learnerId, @Param("now") LocalDateTime now);

    /**
     * Consomme un examen du quota, de façon atomique : la condition est dans la requête, donc deux démarrages simultanés
     * ne peuvent pas dépasser le quota. Renvoie 0 si le droit n'est plus utilisable ou si le quota est épuisé.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SimAccessGrant g SET g.examAttemptsUsed = g.examAttemptsUsed + 1 WHERE g.id = :id "
            + "AND g.status = com.modeltech.datamasteryhub.modules.simulator.entity.GrantStatus.ACTIVE "
            + "AND g.examAttemptsUsed < g.examAttemptsTotal AND g.expiresAt > :now")
    int consumeExamAttempt(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
