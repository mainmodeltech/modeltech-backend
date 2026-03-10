package com.modeltech.datamasteryhub.modules.auth.scheduler;

import com.modeltech.datamasteryhub.modules.auth.repository.PasswordResetTokenRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Nettoyage périodique des tables d'auth.
 *
 * Activer le scheduling dans la classe principale :
 *   @EnableScheduling sur @SpringBootApplication
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthCleanupScheduler {

    private final TokenBlacklistRepository     tokenBlacklistRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Supprime les tokens JWT expirés de la blacklist — toutes les heures.
     */
    @Scheduled(fixedRate = 3_600_000) // 1h en ms
    @Transactional
    public void cleanExpiredBlacklistTokens() {
        tokenBlacklistRepository.deleteExpired(LocalDateTime.now());
        log.debug("Blacklist JWT : nettoyage des tokens expirés effectué");
    }

    /**
     * Supprime les tokens de reset expirés/utilisés — toutes les 6h.
     */
    @Scheduled(fixedRate = 21_600_000) // 6h en ms
    @Transactional
    public void cleanExpiredResetTokens() {
        passwordResetTokenRepository.deleteExpiredAndUsed(LocalDateTime.now());
        log.debug("Reset tokens : nettoyage effectué");
    }
}
