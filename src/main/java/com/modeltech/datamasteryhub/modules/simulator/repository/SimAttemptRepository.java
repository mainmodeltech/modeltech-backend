package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.AttemptMode;
import com.modeltech.datamasteryhub.modules.simulator.entity.AttemptStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimAttemptRepository extends JpaRepository<SimAttempt, UUID> {

    /** Une tentative n'est jamais lue sans son propriétaire : un identifiant d'un autre apprenant est « introuvable ». */
    Optional<SimAttempt> findByIdAndLearnerId(UUID id, UUID learnerId);

    List<SimAttempt> findByLearnerIdOrderByStartedAtDesc(UUID learnerId, Pageable pageable);

    Optional<SimAttempt> findFirstByLearnerIdAndModeAndStatus(UUID learnerId, AttemptMode mode, AttemptStatus status);

    @Query("SELECT a FROM SimAttempt a WHERE a.status = com.modeltech.datamasteryhub.modules.simulator.entity.AttemptStatus.IN_PROGRESS "
            + "AND a.deadlineAt IS NOT NULL AND a.deadlineAt < :cutoff")
    List<SimAttempt> findOverdue(@Param("cutoff") LocalDateTime cutoff);

    /** Combien de fois l'apprenant a déjà vu chaque version de question (pour privilégier les questions jamais vues). */
    @Query("SELECT fi.itemVersion.id, COUNT(fi) FROM SimExamFormItem fi JOIN SimAttempt a ON a.form.id = fi.form.id "
            + "WHERE a.learner.id = :learnerId GROUP BY fi.itemVersion.id")
    List<Object[]> seenCounts(@Param("learnerId") UUID learnerId);
}
