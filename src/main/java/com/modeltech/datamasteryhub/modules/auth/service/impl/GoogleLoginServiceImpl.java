package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthOptionsResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.auth.service.GoogleIdTokenVerifier;
import com.modeltech.datamasteryhub.modules.auth.service.GoogleLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Connexion par Google. Aucune création de compte : un compte (back-office ou apprenant) est créé par l'équipe
 * ou à la confirmation d'un paiement, jamais par un inconnu qui possède une adresse Google. L'adresse Google
 * doit être vérifiée par Google et correspondre exactement à celle du compte.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleLoginServiceImpl implements GoogleLoginService {

    private final GoogleIdTokenVerifier verifier;
    private final AuthService authService;
    private final AdminUserRepository adminUserRepository;
    private final LearnerRepository learnerRepository;

    @Override
    public AuthOptionsResponse options() {
        return AuthOptionsResponse.builder()
                .password(true).passwordless(true)
                .google(verifier.enabled())
                .googleClientId(verifier.enabled() ? verifier.clientId() : null)
                .build();
    }

    @Override
    public AuthResponse login(String credential) {
        if (!verifier.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connexion Google non activée.");
        GoogleIdTokenVerifier.GoogleIdentity identity = verifier.verify(credential)
                .filter(GoogleIdTokenVerifier.GoogleIdentity::emailVerified)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connexion Google refusée."));

        String email = identity.email().toLowerCase(Locale.ROOT).trim();
        boolean known = adminUserRepository.findByEmailAndIsDeletedFalse(email).isPresent()
                || learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).isPresent();
        if (!known) {
            log.info("Connexion Google : aucun compte pour cette adresse");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Aucun compte Model Technologie ne correspond à ce compte Google.");
        }
        return authService.loginWithoutPassword(email);
    }
}
