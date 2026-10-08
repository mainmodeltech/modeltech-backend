package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends SoftDeleteRepository<Enrollment, UUID> {

    Optional<Enrollment> findByRegistrationIdAndIsDeletedFalse(UUID registrationId);

    /** Apprenants d'une session (suivi), avec leur compte et leur inscription. */
    @Query("""
            SELECT e FROM Enrollment e
            JOIN FETCH e.learner
            JOIN FETCH e.registration
            WHERE e.session.id = :sessionId AND e.isDeleted = false
              AND e.status IN (com.modeltech.datamasteryhub.modules.training.enums.EnrollmentStatus.ACTIVE,
                               com.modeltech.datamasteryhub.modules.training.enums.EnrollmentStatus.COMPLETED)
            ORDER BY e.learner.firstName, e.learner.lastName
            """)
    List<Enrollment> findActiveBySession(@Param("sessionId") UUID sessionId);

    /** Accès en cours ou terminés d'un apprenant, avec la formation (via l'inscription) et la session. */
    @Query("""
            SELECT e FROM Enrollment e
            JOIN FETCH e.registration r
            JOIN FETCH r.bootcamp b
            LEFT JOIN FETCH b.partner
            LEFT JOIN FETCH e.session
            WHERE e.learner.id = :learnerId AND e.isDeleted = false
              AND e.status IN (com.modeltech.datamasteryhub.modules.training.enums.EnrollmentStatus.ACTIVE,
                               com.modeltech.datamasteryhub.modules.training.enums.EnrollmentStatus.COMPLETED)
            ORDER BY e.createdAt DESC
            """)
    List<Enrollment> findAccessibleByLearner(@Param("learnerId") UUID learnerId);
}
