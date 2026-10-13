package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    Optional<AuthSession> findByPreviousTokenHash(String previousTokenHash);

    List<AuthSession> findAllBySubjectTypeAndSubjectIdAndRevokedAtIsNull(String subjectType, UUID subjectId);

    /** Sessions terminées depuis plus de 30 jours (expirées ou révoquées) : conservées un mois pour l'enquête, puis supprimées. */
    @Modifying
    @Query("DELETE FROM AuthSession s WHERE (s.expiresAt < :cutoff OR s.maxExpiresAt < :cutoff OR s.revokedAt < :cutoff)")
    int deleteEndedBefore(LocalDateTime cutoff);
}
