package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminRecoveryCode;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminRecoveryCodeRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.security.Base32;
import com.modeltech.datamasteryhub.modules.auth.security.MfaCrypto;
import com.modeltech.datamasteryhub.modules.auth.security.Totp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Authentification à deux facteurs (TOTP) des comptes du back-office : enrôlement, vérification, codes de
 * secours, réinitialisation. Les secrets sont chiffrés ({@link MfaCrypto}) ; un code ne sert qu'une fois
 * (suivi du dernier pas accepté) ; 5 échecs verrouillent la vérification 15 minutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    public static final int RECOVERY_CODE_COUNT = 10;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    /** Sans I, O, 0, 1 : pas de confusion à la saisie d'un code lu sur papier. */
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminUserRepository adminUserRepository;
    private final AdminRecoveryCodeRepository recoveryCodeRepository;
    private final MfaCrypto crypto;

    @Value("${app.auth.mfa.issuer:Model Technologie}")
    private String issuer;

    /** Début d'enrôlement : un secret en attente, activé seulement par {@link #confirmEnrollment}. */
    public record Enrollment(String secret, String otpauthUri) {}

    /** Résultat d'une vérification réussie. */
    public record Verification(boolean recoveryCodeUsed, long remainingRecoveryCodes) {}

    @Transactional
    public Enrollment beginEnrollment(AdminUser admin) {
        requireAvailable();
        if (admin.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La double authentification est déjà activée sur ce compte.");
        }
        byte[] secret = Totp.newSecret();
        admin.setMfaSecretEnc(crypto.encrypt(Base32.encode(secret)));
        admin.setMfaEnabledAt(null);
        admin.setMfaLastStep(null);
        adminUserRepository.save(admin);
        return new Enrollment(Base32.encode(secret), Totp.uri(issuer, admin.getEmail(), secret));
    }

    /** Active le MFA si le code (de l'application) correspond au secret en attente ; renvoie les codes de secours, affichés une seule fois. */
    @Transactional
    public List<String> confirmEnrollment(AdminUser admin, String code) {
        requireAvailable();
        if (admin.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La double authentification est déjà activée sur ce compte.");
        }
        if (admin.getMfaSecretEnc() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun enrôlement en cours : recommencez.");
        }
        requireNotLocked(admin);
        OptionalLong step = Totp.verify(secretOf(admin), normalize(code), now(), -1);
        if (step.isEmpty()) {
            registerFailure(admin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code incorrect. Vérifiez l'heure de votre téléphone et réessayez.");
        }
        admin.setMfaEnabledAt(LocalDateTime.now());
        admin.setMfaLastStep(step.getAsLong());
        admin.setMfaFailedAttempts(0);
        admin.setMfaLockedUntil(null);
        adminUserRepository.save(admin);
        return generateRecoveryCodes(admin);
    }

    /**
     * Vérifie un code de l'application (6 chiffres) ou un code de secours (usage unique).
     *
     * @throws ResponseStatusException 401 code invalide, 429 trop d'essais
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Verification verify(AdminUser admin, String submitted) {
        requireAvailable();
        if (!admin.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La double authentification n'est pas activée sur ce compte.");
        }
        requireNotLocked(admin);
        String code = normalize(submitted);
        if (code.matches("\\d{" + Totp.DIGITS + "}")) {
            long last = admin.getMfaLastStep() == null ? -1 : admin.getMfaLastStep();
            OptionalLong step = Totp.verify(secretOf(admin), code, now(), last);
            if (step.isPresent()) {
                admin.setMfaLastStep(step.getAsLong());
                succeed(admin);
                return new Verification(false, remainingRecoveryCodes(admin));
            }
        } else {
            String hash = crypto.hmac(normalizeRecovery(submitted));
            var found = recoveryCodeRepository.findByAdminUserIdAndCodeHashAndUsedAtIsNull(admin.getId(), hash);
            if (found.isPresent()) {
                found.get().setUsedAt(LocalDateTime.now());
                recoveryCodeRepository.save(found.get());
                succeed(admin);
                log.warn("Code de secours MFA utilisé ({} restant(s))", remainingRecoveryCodes(admin));
                return new Verification(true, remainingRecoveryCodes(admin));
            }
        }
        registerFailure(admin);
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code de vérification incorrect.");
    }

    /** Nouveaux codes de secours (les précédents sont invalidés). L'appelant a déjà vérifié un code valide. */
    @Transactional
    public List<String> regenerateRecoveryCodes(AdminUser admin) {
        requireAvailable();
        return generateRecoveryCodes(admin);
    }

    /** Retire le MFA d'un compte (perte du téléphone) : il devra être enrôlé de nouveau à la prochaine connexion. */
    @Transactional
    public void reset(AdminUser admin) {
        admin.setMfaSecretEnc(null);
        admin.setMfaEnabledAt(null);
        admin.setMfaLastStep(null);
        admin.setMfaFailedAttempts(0);
        admin.setMfaLockedUntil(null);
        adminUserRepository.save(admin);
        recoveryCodeRepository.deleteAllFor(admin.getId());
    }

    public boolean available() {
        return crypto.available();
    }

    // ── privé ───────────────────────────────────────────────────────

    private List<String> generateRecoveryCodes(AdminUser admin) {
        recoveryCodeRepository.deleteAllFor(admin.getId());
        recoveryCodeRepository.flush();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder raw = new StringBuilder();
            for (int j = 0; j < 10; j++) raw.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            AdminRecoveryCode entity = new AdminRecoveryCode();
            entity.setAdminUserId(admin.getId());
            entity.setCodeHash(crypto.hmac(raw.toString()));
            recoveryCodeRepository.save(entity);
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        return codes;
    }

    private long remainingRecoveryCodes(AdminUser admin) {
        return recoveryCodeRepository.countByAdminUserIdAndUsedAtIsNull(admin.getId());
    }

    private void succeed(AdminUser admin) {
        admin.setMfaFailedAttempts(0);
        admin.setMfaLockedUntil(null);
        adminUserRepository.save(admin);
    }

    private void registerFailure(AdminUser admin) {
        int attempts = admin.getMfaFailedAttempts() + 1;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            admin.setMfaFailedAttempts(0);
            admin.setMfaLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            log.warn("Vérification MFA verrouillée {} minutes après {} échecs", LOCK_MINUTES, attempts);
        } else {
            admin.setMfaFailedAttempts(attempts);
        }
        adminUserRepository.save(admin);
    }

    private void requireNotLocked(AdminUser admin) {
        LocalDateTime until = admin.getMfaLockedUntil();
        if (until != null && until.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de codes incorrects. Réessayez dans quelques minutes.");
        }
    }

    private void requireAvailable() {
        if (!crypto.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La double authentification n'est pas configurée sur ce serveur. Contactez l'administrateur.");
        }
    }

    private byte[] secretOf(AdminUser admin) {
        return Base32.decode(crypto.decrypt(admin.getMfaSecretEnc()));
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private static String normalize(String code) {
        return code == null ? "" : code.replaceAll("\\s", "");
    }

    private static String normalizeRecovery(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }
}
