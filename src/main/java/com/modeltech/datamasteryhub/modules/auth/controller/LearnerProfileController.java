package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.modules.auth.dto.LearnerProfilePayloads;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Profil de l'apprenant connecté. L'adresse e-mail (identifiant de connexion) n'est jamais modifiable ici.
 * Le changement de mot de passe d'un compte qui en a un reste {@code PUT /auth/change-password}.
 */
@RestController
@RequestMapping("/api/v1/learner")
@RequiredArgsConstructor
@Tag(name = "Apprenant - Profil")
public class LearnerProfileController {

    private final LearnerRepository learnerRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public LearnerProfilePayloads.Profile profile(Authentication authentication) {
        return toProfile(current(authentication));
    }

    @PutMapping("/profile")
    @Transactional
    public LearnerProfilePayloads.Profile update(@Valid @RequestBody LearnerProfilePayloads.UpdateProfileRequest request,
                                                 Authentication authentication) {
        Learner learner = current(authentication);
        learner.setFirstName(request.getFirstName().trim());
        learner.setLastName(request.getLastName() == null ? "" : request.getLastName().trim());
        learner.setPhone(blankToNull(request.getPhone()));
        learner.setCountry(blankToNull(request.getCountry()));
        return toProfile(learnerRepository.save(learner));
    }

    /**
     * Premier mot de passe d'un compte qui n'en a pas (créé par invitation, lien de connexion ou Google).
     * Si un mot de passe existe déjà : {@code PUT /auth/change-password} (le mot de passe actuel est exigé).
     */
    @PutMapping("/password")
    @Transactional
    public ResponseEntity<Void> setFirstPassword(@Valid @RequestBody LearnerProfilePayloads.SetPasswordRequest request,
                                                 Authentication authentication) {
        Learner learner = current(authentication);
        if (learner.getPasswordHash() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un mot de passe existe déjà : utilisez « Changer mon mot de passe ».");
        }
        learner.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        learnerRepository.save(learner);
        return ResponseEntity.noContent().build();
    }

    private Learner current(Authentication authentication) {
        return learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Espace réservé aux apprenants."));
    }

    private static LearnerProfilePayloads.Profile toProfile(Learner l) {
        return LearnerProfilePayloads.Profile.builder()
                .id(l.getId().toString())
                .email(l.getEmail())
                .firstName(l.getFirstName())
                .lastName(l.getLastName())
                .fullName(l.getFullName())
                .phone(l.getPhone())
                .country(l.getCountry())
                .emailVerified(l.getEmailVerifiedAt() != null)
                .hasPassword(l.getPasswordHash() != null)
                .lastLoginAt(l.getLastLoginAt())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
