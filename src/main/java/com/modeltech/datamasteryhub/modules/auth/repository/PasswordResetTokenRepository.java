package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    // Invalider tous les tokens précédents d'un email avant d'en créer un nouveau
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.email = :email AND t.used = false")
    void invalidateAllByEmail(String email);

    // Nettoyage des tokens expirés (appelé par le scheduler)
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now OR t.used = true")
    void deleteExpiredAndUsed(LocalDateTime now);
}
