package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.common.util.TokenUtils;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.LoginChallenge;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LoginChallengeRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.auth.service.PasswordlessLoginService;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordlessLoginServiceImpl implements PasswordlessLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final Duration MIN_DELAY_BETWEEN_REQUESTS = Duration.ofSeconds(60);
    private static final String INVALID_LINK = "Lien ou code invalide, expiré ou déjà utilisé. Demandez-en un nouveau.";

    private final LoginChallengeRepository challengeRepository;
    private final AdminUserRepository adminUserRepository;
    private final LearnerRepository learnerRepository;
    private final AuthService authService;
    private final MessageDispatcher dispatcher;

    @Value("${app.auth.passwordless.minutes:10}")
    private int validityMinutes;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.frontend.magic-link-path:/connexion/lien}")
    private String magicLinkPath;

    @Override
    @Transactional
    public void request(String rawEmail) {
        String email = normalize(rawEmail);
        Optional<Recipient> recipient = findActiveRecipient(email);
        if (recipient.isEmpty()) {
            log.info("Connexion sans mot de passe demandée pour un compte inconnu ou désactivé (silencieux)");
            return;
        }
        boolean tooSoon = challengeRepository.findFirstByEmailOrderByCreatedAtDesc(email)
                .filter(c -> c.getCreatedAt().isAfter(LocalDateTime.now().minus(MIN_DELAY_BETWEEN_REQUESTS)))
                .isPresent();
        if (tooSoon) {
            log.info("Connexion sans mot de passe : demande trop rapprochée ignorée");
            return;
        }

        String token = TokenUtils.randomToken();
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));

        challengeRepository.consumeAllByEmail(email);
        LoginChallenge challenge = new LoginChallenge();
        challenge.setEmail(email);
        challenge.setTokenHash(sha256(token));
        challenge.setCodeHash(codeHash(email, code));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(validityMinutes));
        challengeRepository.save(challenge);

        String link = frontendUrl + magicLinkPath + "?token=" + token;
        String body = """
                Bonjour %s,

                Pour vous connecter à Model Technologie, utilisez l'une de ces deux méthodes (valables %d minutes, une seule fois) :

                1. Cliquez sur ce lien :
                %s

                2. Ou saisissez ce code sur la page de connexion : %s

                Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : personne ne peut se connecter sans lui.

                L'équipe Model Technologie
                """.formatted(recipient.get().name(), validityMinutes, link, code);
        dispatcher.sendAll(List.of(new OutboundMessage("LOGIN_LINK", email, recipient.get().phone(), recipient.get().name(),
                "Votre lien de connexion Model Technologie", body, null)), null);
    }

    @Override
    @Transactional
    public AuthResponse verifyLink(String token) {
        if (token == null || token.isBlank()) throw invalid();
        LoginChallenge challenge = challengeRepository.findByTokenHash(sha256(token.trim()))
                .filter(LoginChallenge::isUsable)
                .orElseThrow(PasswordlessLoginServiceImpl::invalid);
        challenge.setConsumed(true);
        challengeRepository.save(challenge);
        return open(challenge.getEmail());
    }

    // Le compteur d'essais doit survivre à l'échec renvoyé au client.
    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse verifyCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        LoginChallenge challenge = challengeRepository.findFirstByEmailAndConsumedFalseOrderByCreatedAtDesc(email)
                .filter(LoginChallenge::isUsable)
                .orElseThrow(PasswordlessLoginServiceImpl::invalid);

        String expected = challenge.getCodeHash();
        String given = code == null ? "" : codeHash(email, code.trim());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (challenge.getFailedAttempts() >= MAX_CODE_ATTEMPTS) {
                challenge.setConsumed(true);
                log.warn("Code de connexion annulé après {} essais ratés", MAX_CODE_ATTEMPTS);
            }
            challengeRepository.save(challenge);
            throw invalid();
        }
        challenge.setConsumed(true);
        challengeRepository.save(challenge);
        return open(email);
    }

    // ── Outils ───────────────────────────────────────────────────────

    private AuthResponse open(String email) {
        try {
            return authService.loginWithoutPassword(email);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            throw invalid();   // compte supprimé entre la demande et la validation
        }
    }

    private Optional<Recipient> findActiveRecipient(String email) {
        Optional<AdminUser> admin = adminUserRepository.findByEmailAndIsDeletedFalse(email).filter(AdminUser::isActive);
        if (admin.isPresent()) return Optional.of(new Recipient(admin.get().getFullName(), null));
        return learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).filter(Learner::isActive)
                .map(l -> new Recipient(l.getFullName(), l.getPhone()));
    }

    private record Recipient(String name, String phone) {}

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.toLowerCase(Locale.ROOT).trim();
    }

    private static String codeHash(String email, String code) {
        return sha256(email + ":" + code);
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
