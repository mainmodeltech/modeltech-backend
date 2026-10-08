package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.LoginChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, UUID> {

    Optional<LoginChallenge> findByTokenHash(String tokenHash);

    Optional<LoginChallenge> findFirstByEmailAndConsumedFalseOrderByCreatedAtDesc(String email);

    Optional<LoginChallenge> findFirstByEmailOrderByCreatedAtDesc(String email);

    /** Une nouvelle demande annule les précédentes (un seul lien valable à la fois). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE LoginChallenge c SET c.consumed = true WHERE c.email = :email AND c.consumed = false")
    void consumeAllByEmail(String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM LoginChallenge c WHERE c.expiresAt < :now OR c.consumed = true")
    void deleteExpiredAndConsumed(LocalDateTime now);
}
