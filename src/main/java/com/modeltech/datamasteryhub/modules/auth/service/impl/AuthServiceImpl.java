package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.common.util.LogMask;
import com.modeltech.datamasteryhub.common.util.TokenUtils;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.AuthSession;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.PasswordResetToken;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.entity.TokenBlacklist;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.PasswordResetTokenRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.TokenBlacklistRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AccountSecurityService;
import com.modeltech.datamasteryhub.modules.auth.service.AuthOutcome;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.auth.service.AuthSessionService;
import com.modeltech.datamasteryhub.modules.auth.service.ClientInfo;
import com.modeltech.datamasteryhub.modules.auth.service.LoginThrottle;
import com.modeltech.datamasteryhub.modules.auth.service.MfaPolicy;
import com.modeltech.datamasteryhub.modules.auth.service.MfaService;
import com.modeltech.datamasteryhub.modules.auth.service.PasswordPolicy;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.security.AppUserDetails;
import com.modeltech.datamasteryhub.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentification unifiée : un même e-mail/mot de passe ouvre soit un compte de back-office
 * ({@code admin_users}), soit un compte apprenant ({@code learners}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AdminUserRepository          adminUserRepository;
    private final LearnerRepository            learnerRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenBlacklistRepository     tokenBlacklistRepository;
    private final AuthenticationManager        authenticationManager;
    private final JwtTokenProvider             jwtTokenProvider;
    private final PasswordEncoder              passwordEncoder;
    private final NotificationService          notificationService;
    private final UserDetailsServiceImpl       userDetailsService;
    private final AuthSessionService           sessions;
    private final AccountSecurityService       accountSecurity;
    private final MfaPolicy                    mfaPolicy;
    private final MfaService                   mfaService;
    private final LoginThrottle                throttle;
    private final PasswordPolicy               passwordPolicy;

    @Value("${app.password-reset.expiration-minutes:15}")
    private int resetTokenExpirationMinutes;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Echecs de mot de passe consecutifs sur un meme compte (toutes adresses IP confondues) avant verrouillage
     * temporaire. Volontairement eleve : le frein principal est le couple (e-mail, IP) de {@link LoginThrottle} et le
     * second facteur ; ce seuil ne vise que les attaques reparties sur de nombreuses adresses.
     */
    @Value("${app.auth.lockout.max-attempts:20}")
    private int lockoutMaxAttempts;

    @Value("${app.auth.lockout.minutes:15}")
    private int lockoutMinutes;

    /** Route frontend (hors /admin) où l'apprenant définit ou réinitialise son mot de passe. */
    @Value("${app.frontend.learner-reset-path:/reinitialiser-mot-de-passe}")
    private String learnerResetPath;

    // ─────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────

    // Les echecs sont comptes : la transaction ne doit pas etre annulee par l'exception qui remonte au client.
    @Override
    @Transactional(noRollbackFor = {BadCredentialsException.class, ResponseStatusException.class})
    public AuthOutcome login(LoginRequest request, ClientInfo client) {
        String email = request.getEmail();
        log.info("Tentative de connexion: {}", LogMask.email(email));

        long blocked = throttle.blockedMinutes(email, client.ip());
        if (blocked > 0) throw tooManyAttempts(blocked);
        requireNotLocked(email);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (BadCredentialsException e) {
            throttle.recordFailure(email, client.ip());
            registerFailedAttempt(email);
            throw e;
        }
        throttle.reset(email, client.ip());
        resetFailedAttempts(email);
        return completeLogin((AppUserDetails) authentication.getPrincipal(), client);
    }

    @Override
    @Transactional
    public AuthOutcome loginWithoutPassword(String email, ClientInfo client) {
        AppUserDetails details = (AppUserDetails) userDetailsService.loadUserByUsername(email);
        if (!details.isAccountNonLocked()) throw new LockedException("Compte désactivé");
        learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).ifPresent(learner -> {
            if (learner.getEmailVerifiedAt() == null) {
                learner.setEmailVerifiedAt(LocalDateTime.now());   // le lien, le code ou Google l'ont prouvé
                learnerRepository.save(learner);
            }
        });
        return completeLogin(details, client);
    }

    /** Premier facteur validé : session tout de suite, ou défi MFA pour le personnel. */
    private AuthOutcome completeLogin(AppUserDetails user, ClientInfo client) {
        if (mfaPolicy.enforcedFor(user)) {
            if (!mfaService.available()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "La double authentification n'est pas configurée sur ce serveur. Contactez l'administrateur.");
            }
            boolean enrolled = user.mfaEnabled();
            String challenge = jwtTokenProvider.generateMfaChallengeToken(user,
                    enrolled ? JwtTokenProvider.PURPOSE_VERIFY : JwtTokenProvider.PURPOSE_ENROLL);
            log.info("Second facteur requis ({}) pour {}", enrolled ? "vérification" : "enrôlement", LogMask.email(user.getUsername()));
            return AuthOutcome.withoutCookie(AuthResponse.builder()
                    .tokenType("Bearer")
                    .mfaRequired(enrolled)
                    .mfaEnrollmentRequired(!enrolled)
                    .mfaToken(challenge)
                    .build());
        }
        return startSession(user, false, client);
    }

    @Override
    @Transactional
    public AuthOutcome startSession(AppUserDetails user, boolean mfaVerified, ClientInfo client) {
        AuthResponse.AdminUserResponse profile = recordLoginAndProfile(user.getUsername());
        String type = user.isStaff() ? AuthSession.ADMIN : AuthSession.LEARNER;
        AuthSessionService.Issued issued = sessions.start(type, user.id(), mfaVerified, client);
        String accessToken = jwtTokenProvider.generateAccessToken(user, issued.sessionId(), mfaVerified);
        log.info("Connexion réussie: {}", LogMask.email(user.getUsername()));
        return new AuthOutcome(AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .user(profile)
                .build(), issued.refreshToken(), Duration.between(LocalDateTime.now(), issued.expiresAt()));
    }

    /** Horodatage de connexion et remise à zéro du compteur d'échecs ; renvoie le profil. */
    private AuthResponse.AdminUserResponse recordLoginAndProfile(String email) {
        return adminUserRepository.findByEmailAndIsDeletedFalse(email)
                .map(admin -> {
                    admin.setLastLoginAt(LocalDateTime.now());
                    admin.setFailedLoginAttempts(0);
                    admin.setLockedUntil(null);
                    adminUserRepository.save(admin);
                    return toProfile(admin);
                })
                .or(() -> learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).map(learner -> {
                    learner.setLastLoginAt(LocalDateTime.now());
                    learner.setFailedLoginAttempts(0);
                    learner.setLockedUntil(null);
                    learnerRepository.save(learner);
                    return toProfile(learner);
                }))
                .orElseThrow(() -> new ResourceNotFoundException("Compte", "email", email));
    }

    // ─────────────────────────────────────────────────────────────────────
    // RAFRAÎCHISSEMENT
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Optional<AuthOutcome> refresh(String refreshToken) {
        Optional<AuthSessionService.Refreshed> refreshed = sessions.refresh(refreshToken);
        if (refreshed.isEmpty()) return Optional.empty();
        AuthSession session = refreshed.get().session();

        String email = AuthSession.ADMIN.equals(session.getSubjectType())
                ? adminUserRepository.findByIdAndIsDeletedFalse(session.getSubjectId()).map(AdminUser::getEmail).orElse(null)
                : learnerRepository.findByIdAndIsDeletedFalse(session.getSubjectId()).map(Learner::getEmail).orElse(null);
        if (email == null) return Optional.empty();

        AppUserDetails user = (AppUserDetails) userDetailsService.loadUserByUsername(email);
        // Une session « second facteur validé » ne survit pas à la disparition du second facteur du compte
        boolean staffWithoutSecondFactor = mfaPolicy.enforcedFor(user) && (!session.isMfaVerified() || !user.mfaEnabled());
        if (!user.isAccountNonLocked() || staffWithoutSecondFactor) {
            sessions.revokeAllFor(session.getSubjectType(), session.getSubjectId());
            return Optional.empty();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user, session.getId(), session.isMfaVerified());
        AuthResponse body = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .user(me(email))
                .build();
        String newToken = refreshed.get().newRefreshToken();
        return Optional.of(new AuthOutcome(body, newToken,
                newToken == null ? null : Duration.between(LocalDateTime.now(), session.getExpiresAt())));
    }

    // ─────────────────────────────────────────────────────────────────────
    // ME
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AuthResponse.AdminUserResponse me(String email) {
        return adminUserRepository.findByEmailAndIsDeletedFalse(email).map(this::toProfile)
                .or(() -> learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).map(this::toProfile))
                .orElseThrow(() -> new ResourceNotFoundException("Compte", "email", email));
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOGOUT — fin de session + révocation du jeton d'accès
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        sessions.revoke(refreshToken);
        if (accessToken == null || accessToken.isBlank()) return;
        try {
            String email = jwtTokenProvider.getEmailFromToken(accessToken);
            String tokenHash = sha256(accessToken);
            if (!tokenBlacklistRepository.existsByTokenHash(tokenHash)) {
                tokenBlacklistRepository.save(TokenBlacklist.builder()
                        .tokenHash(tokenHash)
                        .email(email)
                        .expiresAt(LocalDateTime.now().plusNanos(jwtTokenProvider.getExpiration() * 1_000_000L))
                        .build());
            }
            log.info("Session terminée pour: {}", LogMask.email(email));
        } catch (Exception e) {
            // Jeton d'accès déjà expiré ou malformé : la session, elle, est terminée
            log.debug("Logout : jeton d'accès non exploitable ({})", e.getClass().getSimpleName());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CHANGE PASSWORD
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        Optional<AdminUser> admin = adminUserRepository.findByEmailAndIsDeletedFalse(email);
        if (admin.isPresent()) {
            requireCurrentPassword(request.getCurrentPassword(), admin.get().getPasswordHash());
            passwordPolicy.validate(request.getNewPassword(), email, true);
            admin.get().setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            accountSecurity.invalidateSessions(admin.get());
        } else {
            Learner learner = learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Compte", "email", email));
            requireCurrentPassword(request.getCurrentPassword(), learner.getPasswordHash());
            passwordPolicy.validate(request.getNewPassword(), email, false);
            learner.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            accountSecurity.invalidateSessions(learner);
        }
        log.info("Mot de passe modifié pour: {}", LogMask.email(email));
    }

    private void requireCurrentPassword(String provided, String currentHash) {
        if (currentHash == null || !passwordEncoder.matches(provided, currentHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // FORGOT PASSWORD
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT).trim();

        // Sécurité : on ne révèle pas si l'email existe ou non (réponse identique côté API)
        Optional<AdminUser> admin = adminUserRepository.findByEmailAndIsDeletedFalse(email);
        Optional<Learner> learner = admin.isPresent()
                ? Optional.empty()
                : learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email);

        boolean activeAccount = admin.map(AdminUser::isActive).orElse(false)
                || learner.map(Learner::isActive).orElse(false);
        if (!activeAccount) {
            log.warn("Forgot password: email inconnu ou compte désactivé (silencieux) — {}", LogMask.email(email));
            return;
        }

        String rawToken = createPasswordResetToken(email, resetTokenExpirationMinutes);
        String link = passwordSetupLink(rawToken, learner.isPresent());
        notificationService.notifyPasswordResetEmail(email, link, resetTokenExpirationMinutes);

        log.info("Token de réinitialisation créé pour: {} (expire dans {} min)", LogMask.email(email), resetTokenExpirationMinutes);
    }

    // ─────────────────────────────────────────────────────────────────────
    // RESET PASSWORD
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(sha256(request.getToken()))   // le jeton n'est stocké que haché
                .filter(PasswordResetToken::isValid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Lien de réinitialisation invalide ou expiré."));

        String email = resetToken.getEmail();

        Optional<AdminUser> admin = adminUserRepository.findByEmailAndIsDeletedFalse(email);
        // Le jeton n'est consommé que si le mot de passe est accepté : un refus de la politique laisse le lien utilisable
        passwordPolicy.validate(request.getNewPassword(), email, admin.isPresent());
        String newHash = passwordEncoder.encode(request.getNewPassword());

        if (admin.isPresent()) {
            admin.get().setPasswordHash(newHash);
            accountSecurity.invalidateSessions(admin.get());
        } else {
            Learner learner = learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Lien de réinitialisation invalide ou expiré."));
            learner.setPasswordHash(newHash);
            if (learner.getEmailVerifiedAt() == null) {
                learner.setEmailVerifiedAt(LocalDateTime.now()); // le lien reçu par e-mail le prouve
            }
            accountSecurity.invalidateSessions(learner);
        }

        // Marquer le token comme utilisé
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        log.info("Mot de passe réinitialisé pour: {}", LogMask.email(email));
    }

    // ─────────────────────────────────────────────────────────────────────
    // JETONS DE DÉFINITION DE MOT DE PASSE (reset + invitations)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public String createPasswordResetToken(String email, int validityMinutes) {
        String normalized = email.toLowerCase(Locale.ROOT).trim();
        passwordResetTokenRepository.invalidateAllByEmail(normalized);

        // Seul le hash SHA-256 est conservé en base : une fuite de la table ne donne aucun lien utilisable
        String rawToken = TokenUtils.randomToken();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(sha256(rawToken))
                .email(normalized)
                .expiresAt(LocalDateTime.now().plusMinutes(validityMinutes))
                .build();
        passwordResetTokenRepository.save(resetToken);
        return rawToken;
    }

    @Override
    public String passwordSetupLink(String token, boolean learner) {
        return learner
                ? frontendUrl + learnerResetPath + "?token=" + token
                : frontendUrl + "/admin/reset-password?token=" + token;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS PRIVÉS
    // ─────────────────────────────────────────────────────────────────────

    // ── Verrouillage temporaire après échecs ─────────────────────────

    private ResponseStatusException tooManyAttempts(long minutes) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Trop de tentatives échouées. Réessayez dans " + minutes
                        + " minute(s), ou connectez-vous avec un lien envoyé par e-mail.");
    }

    private void requireNotLocked(String email) {
        LocalDateTime until = adminUserRepository.findByEmailAndIsDeletedFalse(email).map(AdminUser::getLockedUntil)
                .or(() -> learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).map(Learner::getLockedUntil))
                .orElse(null);
        if (until != null && until.isAfter(LocalDateTime.now())) {
            throw tooManyAttempts(Math.max(1, Duration.between(LocalDateTime.now(), until).toMinutes() + 1));
        }
    }

    private void resetFailedAttempts(String email) {
        adminUserRepository.findByEmailAndIsDeletedFalse(email).ifPresent(a -> {
            if (a.getFailedLoginAttempts() != 0 || a.getLockedUntil() != null) {
                a.setFailedLoginAttempts(0);
                a.setLockedUntil(null);
                adminUserRepository.save(a);
            }
        });
        learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).ifPresent(l -> {
            if (l.getFailedLoginAttempts() != 0 || l.getLockedUntil() != null) {
                l.setFailedLoginAttempts(0);
                l.setLockedUntil(null);
                learnerRepository.save(l);
            }
        });
    }

    private void registerFailedAttempt(String email) {
        adminUserRepository.findByEmailAndIsDeletedFalse(email).ifPresentOrElse(admin -> {
            LocalDateTime until = lockedUntilAfter(admin.getFailedLoginAttempts() + 1, admin.getLockedUntil());
            admin.setFailedLoginAttempts(until != null ? 0 : admin.getFailedLoginAttempts() + 1);
            admin.setLockedUntil(until != null ? until : admin.getLockedUntil());
            adminUserRepository.save(admin);
        }, () -> learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).ifPresent(learner -> {
            LocalDateTime until = lockedUntilAfter(learner.getFailedLoginAttempts() + 1, learner.getLockedUntil());
            learner.setFailedLoginAttempts(until != null ? 0 : learner.getFailedLoginAttempts() + 1);
            learner.setLockedUntil(until != null ? until : learner.getLockedUntil());
            learnerRepository.save(learner);
        }));
    }

    /** Heure de fin du verrouillage si ce nouvel échec atteint le seuil, sinon null. */
    private LocalDateTime lockedUntilAfter(int attempts, LocalDateTime currentLock) {
        if (attempts < lockoutMaxAttempts) return null;
        log.warn("Verrouillage de la connexion par mot de passe pour {} minute(s) après {} échecs", lockoutMinutes, attempts);
        return LocalDateTime.now().plusMinutes(lockoutMinutes);
    }

    private AuthResponse.AdminUserResponse toProfile(AdminUser admin) {
        return AuthResponse.AdminUserResponse.builder()
                .id(admin.getId())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .primaryRole(admin.getPrimaryRole())
                .roles(roleNames(admin.getRoles()))
                .userType(JwtTokenProvider.USER_TYPE_ADMIN)
                .mfaEnabled(admin.isMfaEnabled())
                .partnerId(admin.getPartner() != null ? admin.getPartner().getId() : null)
                .build();
    }

    private AuthResponse.AdminUserResponse toProfile(Learner learner) {
        Set<String> roles = roleNames(learner.getRoles());
        return AuthResponse.AdminUserResponse.builder()
                .id(learner.getId())
                .email(learner.getEmail())
                .fullName(learner.getFullName())
                .primaryRole(roles.stream().sorted().findFirst().orElse(RoleNames.LEARNER))
                .roles(roles)
                .userType(JwtTokenProvider.USER_TYPE_LEARNER)
                .build();
    }

    private Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }

    /**
     * Hash SHA-256 d'un token JWT pour stockage sécurisé en blacklist.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
