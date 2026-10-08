package com.modeltech.datamasteryhub.modules.notification.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.notification.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailLogRepository extends SoftDeleteRepository<EmailLog, UUID> {

    @Query("""
            SELECT l FROM EmailLog l
            WHERE (:status IS NULL OR l.status = :status)
              AND (:type IS NULL OR l.type = :type)
            """)
    Page<EmailLog> search(@Param("status") String status, @Param("type") String type, Pageable pageable);

    Optional<EmailLog> findFirstByStatusOrderByCreatedAtDesc(String status);

    /** Purge des traces anciennes (le journal sert au diagnostic, pas à l'archivage). */
    @Modifying
    @Query("DELETE FROM EmailLog l WHERE l.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
