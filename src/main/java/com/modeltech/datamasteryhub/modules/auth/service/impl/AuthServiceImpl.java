package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.PasswordResetToken;
import com.modeltech.datamasteryhub.modules.auth.entity.TokenBlacklist;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.PasswordResetTokenRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.TokenBlacklistRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AdminUserRepository          adminUserRepository;
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

        AdminUser adminUser = getAdminUserByEmail(request.getEmail());
        adminUser.setLastLoginAt(LocalDateTime.now());
        adminUserRepository.save(adminUser);

        log.info("Connexion réussie: {}", request.getEmail());
        return buildAuthResponse(token, adminUser);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ME
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public AuthResponse.AdminUserResponse me(String email) {
        AdminUser adminUser = getAdminUserByEmail(email);
        return buildAdminUserResponse(adminUser);
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
        AdminUser adminUser = getAdminUserByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), adminUser.getPasswordHash())) {
            throw new BadCredentialsException("Mot de passe actuel incorrect");
        }

        adminUser.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminUserRepository.save(adminUser);
        log.info("Mot de passe modifié pour: {}", email);
    }

    // ─────────────────────────────────────────────────────────────────────
    // FORGOT PASSWORD
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Sécurité : on ne révèle pas si l'email existe ou non
        boolean userExists = adminUserRepository.existsByEmailAndIsDeletedFalse(email);
        if (!userExists) {
            log.warn("Forgot password: email inconnu (silencieux) — {}", email);
            return; // réponse identique pour éviter l'énumération d'emails
        }

        // Invalider les anciens tokens
        passwordResetTokenRepository.invalidateAllByEmail(email);

        // Créer un nouveau token opaque
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .email(email)
                .expiresAt(LocalDateTime.now().plusMinutes(resetTokenExpirationMinutes))
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Envoyer l'email
        String resetLink = frontendUrl + "/admin/reset-password?token=" + rawToken;
        notificationService.notifyPasswordResetEmail(email, resetLink, resetTokenExpirationMinutes);

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
                .orElseThrow(() -> new BadCredentialsException("Token de réinitialisation invalide"));

        if (!resetToken.isValid()) {
            throw new BadCredentialsException("Token expiré ou déjà utilisé");
        }

        AdminUser adminUser = getAdminUserByEmail(resetToken.getEmail());
        adminUser.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminUserRepository.save(adminUser);

        // Marquer le token comme utilisé
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        log.info("Mot de passe réinitialisé pour: {}", resetToken.getEmail());
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS PRIVÉS
    // ─────────────────────────────────────────────────────────────────────

    private AdminUser getAdminUserByEmail(String email) {
        return adminUserRepository
                .findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "email", email));
    }

    private AuthResponse buildAuthResponse(String token, AdminUser adminUser) {
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .user(buildAdminUserResponse(adminUser))
                .build();
    }

    private AuthResponse.AdminUserResponse buildAdminUserResponse(AdminUser adminUser) {
        return AuthResponse.AdminUserResponse.builder()
                .id(adminUser.getId())
                .email(adminUser.getEmail())
                .fullName(adminUser.getFullName())
                .primaryRole(adminUser.getPrimaryRole())
                .roles(adminUser.getRoles().stream()
                        .map(r -> r.getName())
                        .collect(Collectors.toSet()))
                .build();
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