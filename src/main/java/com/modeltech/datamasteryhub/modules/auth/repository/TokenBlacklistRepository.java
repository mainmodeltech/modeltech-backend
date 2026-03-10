package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, UUID> {

    boolean existsByTokenHash(String tokenHash);

    // Nettoyage des tokens expirés (appelé par le scheduler)
    @Modifying
    @Query("DELETE FROM TokenBlacklist t WHERE t.expiresAt < :now")
    void deleteExpired(LocalDateTime now);
}
