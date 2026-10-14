package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.communication.entity.Masterclass;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MasterclassRepository extends SoftDeleteRepository<Masterclass, UUID> {

    Optional<Masterclass> findByCodeAndIsDeletedFalse(String code);

    boolean existsByCode(String code);

    List<Masterclass> findAllByIsDeletedFalseOrderByStartsAtDesc();

    /** Événements publiés pas encore terminés (la fin se calcule : début + durée), les plus proches d'abord. */
    @Query(value = """
            SELECT * FROM masterclasses
            WHERE is_deleted = FALSE AND published = TRUE
              AND starts_at + (duration_minutes * INTERVAL '1 minute') >= :now
            ORDER BY starts_at ASC LIMIT :limit
            """, nativeQuery = true)
    List<Masterclass> upcoming(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
