package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BootcampSessionRepository extends JpaRepository<BootcampSession, UUID> {

    // Toutes les sessions d'un bootcamp (public)
    List<BootcampSession> findAllByBootcampIdAndPublishedTrueAndIsDeletedFalseOrderByStartDateAsc(UUID bootcampId);

    // Toutes les sessions d'un bootcamp (admin)
    List<BootcampSession> findAllByBootcampIdAndIsDeletedFalseOrderByStartDateAsc(UUID bootcampId);

    // Prochaine session mise en avant pour un bootcamp
    Optional<BootcampSession> findFirstByBootcampIdAndIsFeaturedTrueAndPublishedTrueAndIsDeletedFalse(UUID bootcampId);

    // Session mise en avant par défaut : prochaine session OPEN ou UPCOMING
    @Query("""
        SELECT s FROM BootcampSession s
        WHERE s.bootcamp.id = :bootcampId
          AND s.published = true
          AND s.isDeleted = false
          AND s.status IN ('OPEN', 'UPCOMING')
        ORDER BY s.startDate ASC
        LIMIT 1
    """)
    Optional<BootcampSession> findNextUpcomingSession(@Param("bootcampId") UUID bootcampId);

    // Toutes les sessions à venir (pour le tableau de bord)
    @Query("""
        SELECT s FROM BootcampSession s
        WHERE s.isDeleted = false
          AND s.status IN ('OPEN', 'UPCOMING')
        ORDER BY s.startDate ASC
    """)
    List<BootcampSession> findAllUpcomingSessions();

    // Calendrier du catalogue : sessions publiées des formations publiées (rattachées à un domaine visible)
    @Query("""
        SELECT s FROM BootcampSession s
        JOIN FETCH s.bootcamp b
        JOIN FETCH b.domain d
        LEFT JOIN FETCH b.partner
        WHERE s.published = true AND s.isDeleted = false
          AND s.status IN :statuses
          AND b.published = true AND b.isDeleted = false
          AND d.visible = true AND d.isDeleted = false
        ORDER BY s.startDate ASC NULLS LAST, s.id
    """)
    List<BootcampSession> findCatalogueSessions(@Param("statuses") Collection<SessionStatus> statuses);

    Optional<BootcampSession> findByIdAndIsDeletedFalse(UUID sessionId);
}
