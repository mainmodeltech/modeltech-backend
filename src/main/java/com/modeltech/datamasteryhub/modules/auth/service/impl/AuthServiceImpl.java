package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.common.util.TokenUtils;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.PasswordResetToken;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.entity.TokenBlacklist;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.PasswordResetTokenRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.TokenBlacklistRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    @Value("${app.password-reset.expiration-minutes:15}")
    private int resetTokenExpirationMinutes;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /** Route frontend (hors /admin) où l'apprenant définit ou réinitialise son mot de passe. */
    @Value("${app.frontend.learner-reset-path:/reinitialiser-mot-de-passe}")
    private String learnerResetPath;

    // ─────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Tentative de connexion: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = jwtTokenProvider.generateToken(authentication);
        String email = authentication.getName();

        AuthResponse.AdminUserResponse profile = adminUserRepository.findByEmailAndIsDeletedFalse(email)
                .map(admin -> {
                    admin.setLastLoginAt(LocalDateTime.now());
                    adminUserRepository.save(admin);
                    return toProfile(admin);
                })
                .or(() -> learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).map(learner -> {
                    learner.setLastLoginAt(LocalDateTime.now());
                    learnerRepository.save(learner);
                    return toProfile(learner);
                }))
                .orElseThrow(() -> new ResourceNotFoundException("Compte", "email", email));

        log.info("Connexion réussie: {}", email);
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .user(profile)
                .build();
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
    // LOGOUT — révocation JWT via blacklist
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;

        try {
            String email     = jwtTokenProvider.getEmailFromToken(token);
            String tokenHash = sha256(token);
            long   expiresIn = jwtTokenProvider.getExpiration(); // ms

            // Éviter les doublons
            if (!tokenBlacklistRepository.existsByTokenHash(tokenHash)) {
                tokenBlacklistRepository.save(TokenBlacklist.builder()
                        .tokenHash(tokenHash)
                        .email(email)
                        .expiresAt(LocalDateTime.now().plusNanos(expiresIn * 1_000_000L))
                        .build());
            }
            log.info("Token révoqué pour: {}", email);

        } catch (Exception e) {
            // Token malformé / déjà expiré — on ignore
            log.warn("Logout: token invalide ou déjà expiré — {}", e.getMessage());
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
            admin.get().setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            adminUserRepository.save(admin.get());
        } else {
            Learner learner = learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Compte", "email", email));
            requireCurrentPassword(request.getCurrentPassword(), learner.getPasswordHash());
            learner.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            learnerRepository.save(learner);
        }
        log.info("Mot de passe modifié pour: {}", email);
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
            log.warn("Forgot password: email inconnu ou compte désactivé (silencieux) — {}", email);
            return;
        }

        String rawToken = createPasswordResetToken(email, resetTokenExpirationMinutes);
        String link = passwordSetupLink(rawToken, learner.isPresent());
        notificationService.notifyPasswordResetEmail(email, link, resetTokenExpirationMinutes);

        log.info("Token de réinitialisation créé pour: {} (expire dans {} min)", email, resetTokenExpirationMinutes);
    }

    // ─────────────────────────────────────────────────────────────────────
    // RESET PASSWORD
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(request.getToken())
                .filter(PasswordResetToken::isValid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Lien de réinitialisation invalide ou expiré."));

        String email = resetToken.getEmail();
        String newHash = passwordEncoder.encode(request.getNewPassword());

        Optional<AdminUser> admin = adminUserRepository.findByEmailAndIsDeletedFalse(email);
        if (admin.isPresent()) {
            admin.get().setPasswordHash(newHash);
            adminUserRepository.save(admin.get());
        } else {
            Learner learner = learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Lien de réinitialisation invalide ou expiré."));
            learner.setPasswordHash(newHash);
            if (learner.getEmailVerifiedAt() == null) {
                learner.setEmailVerifiedAt(LocalDateTime.now()); // le lien reçu par e-mail le prouve
            }
            learnerRepository.save(learner);
        }

        // Marquer le token comme utilisé
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        log.info("Mot de passe réinitialisé pour: {}", email);
    }

    // ─────────────────────────────────────────────────────────────────────
    // JETONS DE DÉFINITION DE MOT DE PASSE (reset + invitations)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public String createPasswordResetToken(String email, int validityMinutes) {
        String normalized = email.toLowerCase(Locale.ROOT).trim();
        passwordResetTokenRepository.invalidateAllByEmail(normalized);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(TokenUtils.randomToken())
                .email(normalized)
                .expiresAt(LocalDateTime.now().plusMinutes(validityMinutes))
                .build();
        return passwordResetTokenRepository.save(resetToken).getToken();
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

    private AuthResponse.AdminUserResponse toProfile(AdminUser admin) {
        return AuthResponse.AdminUserResponse.builder()
                .id(admin.getId())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .primaryRole(admin.getPrimaryRole())
                .roles(roleNames(admin.getRoles()))
                .userType(JwtTokenProvider.USER_TYPE_ADMIN)
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
