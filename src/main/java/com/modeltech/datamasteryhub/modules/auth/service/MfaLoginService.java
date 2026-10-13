package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.service.impl.UserDetailsServiceImpl;
import com.modeltech.datamasteryhub.security.AppUserDetails;
import com.modeltech.datamasteryhub.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Seconde étape de la connexion du personnel : vérification du code (ou d'un code de secours), ou enrôlement de la
 * double authentification à la première connexion. Ces étapes s'appuient sur le défi MFA délivré après le premier
 * facteur ({@link JwtTokenProvider#generateMfaChallengeToken}) ; ce défi ne donne accès à aucune autre route.
 */
@Service
@RequiredArgsConstructor
public class MfaLoginService {

    private final JwtTokenProvider jwt;
    private final AdminUserRepository adminUserRepository;
    private final UserDetailsServiceImpl userDetailsService;
    private final MfaService mfa;
    private final AuthService authService;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthOutcome verify(String challenge, String code, ClientInfo client) {
        AdminUser admin = resolve(challenge, JwtTokenProvider.PURPOSE_VERIFY);
        MfaService.Verification verification = mfa.verify(admin, code);
        AuthOutcome outcome = open(admin, client);
        if (verification.recoveryCodeUsed()) outcome.body().setRecoveryCodesRemaining(verification.remainingRecoveryCodes());
        return outcome;
    }

    @Transactional
    public MfaService.Enrollment startEnrollment(String challenge) {
        return mfa.beginEnrollment(resolve(challenge, JwtTokenProvider.PURPOSE_ENROLL));
    }

    /** Active le MFA puis ouvre la session ; les codes de secours sont dans la réponse (affichés une seule fois). */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthOutcome confirmEnrollment(String challenge, String code, ClientInfo client) {
        AdminUser admin = resolve(challenge, JwtTokenProvider.PURPOSE_ENROLL);
        List<String> recoveryCodes = mfa.confirmEnrollment(admin, code);
        AuthOutcome outcome = open(admin, client);
        outcome.body().setRecoveryCodes(recoveryCodes);
        return outcome;
    }

    /** Nouveaux codes de secours pour un compte connecté ; exige un code valide (application ou secours) en confirmation. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public List<String> regenerateRecoveryCodes(String email, String code) {
        AdminUser admin = adminUserRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé au personnel."));
        mfa.verify(admin, code);
        return mfa.regenerateRecoveryCodes(admin);
    }

    // ── privé ───────────────────────────────────────────────────────

    private AuthOutcome open(AdminUser admin, ClientInfo client) {
        AppUserDetails user = (AppUserDetails) userDetailsService.loadUserByUsername(admin.getEmail());
        return authService.startSession(user, true, client);
    }

    /** Défi valide (signature, durée, type), compte actif et inchangé depuis, usage conforme à l'étape demandée. */
    private AdminUser resolve(String challenge, String expectedPurpose) {
        var claims = jwt.parseOfType(challenge == null ? "" : challenge, JwtTokenProvider.TYPE_MFA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "La vérification a expiré. Reconnectez-vous."));
        AdminUser admin = adminUserRepository.findByEmailAndIsDeletedFalse(claims.email())
                .filter(AdminUser::isActive)
                .filter(a -> a.getSecurityVersion() == claims.securityVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "La vérification a expiré. Reconnectez-vous."));
        if (!expectedPurpose.equals(claims.purpose())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette étape ne correspond pas à l'état de votre compte. Reconnectez-vous.");
        }
        return admin;
    }
}
