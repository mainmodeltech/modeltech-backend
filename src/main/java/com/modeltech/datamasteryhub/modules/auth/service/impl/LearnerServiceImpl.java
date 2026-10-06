package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.dto.request.CreateLearnerRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.LearnerResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.auth.service.LearnerService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LearnerServiceImpl implements LearnerService {

    /** Validité du lien « définir mon mot de passe » envoyé à la création du compte. */
    static final int INVITATION_VALID_HOURS = 72;

    private final LearnerRepository learnerRepository;
    private final AdminUserRepository adminUserRepository;
    private final RoleRepository roleRepository;
    private final AuthService authService;
    private final NotificationService notificationService;

    // ── Admin ───────────────────────────────────────────────────────

    @Override
    public Page<LearnerResponse> findAllForAdmin(Pageable pageable) {
        return learnerRepository.findAllByIsDeletedFalse(pageable).map(this::toResponse);
    }

    @Override
    public LearnerResponse findByIdForAdmin(UUID id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public LearnerResponse create(CreateLearnerRequest request) {
        String email = normalize(request.getEmail());
        if (adminUserRepository.existsByEmailAndIsDeletedFalse(email)
                || learnerRepository.existsByEmailIgnoreCaseAndIsDeletedFalse(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet e-mail.");
        }
        Learner learner = newLearner(request.getFirstName(), request.getLastName(), email,
                request.getPhone(), request.getCountry());
        log.info("Création du compte apprenant {}", email);
        return toResponse(invite(learnerRepository.save(learner)));
    }

    @Override
    @Transactional
    public LearnerResponse setActive(UUID id, boolean active) {
        Learner learner = getOrThrow(id);
        learner.setActive(active);
        return toResponse(learnerRepository.save(learner));
    }

    @Override
    @Transactional
    public void resendInvitation(UUID id) {
        Learner learner = getOrThrow(id);
        if (learner.getPasswordHash() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet apprenant a déjà défini son mot de passe.");
        }
        invite(learner);
    }

    // ── Inscription → compte ─────────────────────────────────────────

    @Override
    @Transactional
    public Learner findOrCreateInvited(String firstName, String lastName, String email, String phone, String country) {
        String normalized = normalize(email);
        if (adminUserRepository.existsByEmailAndIsDeletedFalse(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet e-mail est celui d'un compte de back-office : impossible de créer un compte apprenant.");
        }
        return learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(normalized)
                .orElseGet(() -> {
                    Learner created = learnerRepository.save(
                            newLearner(firstName, lastName, normalized, phone, country));
                    return invite(created);
                });
    }

    // ── Privé ───────────────────────────────────────────────────────

    private Learner newLearner(String firstName, String lastName, String email, String phone, String country) {
        Role learnerRole = roleRepository.findByName(RoleNames.LEARNER)
                .orElseThrow(() -> new IllegalStateException("Rôle " + RoleNames.LEARNER + " absent (migration V20 ?)"));
        Learner learner = new Learner();
        learner.setEmail(email);
        learner.setFirstName(firstName.trim());
        learner.setLastName(lastName == null ? "" : lastName.trim());
        learner.setPhone(blankToNull(phone));
        learner.setCountry(blankToNull(country));
        learner.getRoles().add(learnerRole);
        return learner;
    }

    /** Émet le jeton « définir mon mot de passe » et envoie l'e-mail d'invitation. */
    private Learner invite(Learner learner) {
        String token = authService.createPasswordResetToken(learner.getEmail(), INVITATION_VALID_HOURS * 60);
        notificationService.sendAccountInvitationEmail(learner.getEmail(), learner.getFirstName(),
                authService.passwordSetupLink(token, true), INVITATION_VALID_HOURS, true);
        return learner;
    }

    private Learner getOrThrow(UUID id) {
        return learnerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Apprenant", "id", id));
    }

    private LearnerResponse toResponse(Learner l) {
        return LearnerResponse.builder()
                .id(l.getId())
                .email(l.getEmail())
                .firstName(l.getFirstName())
                .lastName(l.getLastName())
                .phone(l.getPhone())
                .country(l.getCountry())
                .active(l.isActive())
                .passwordSet(l.getPasswordHash() != null)
                .emailVerifiedAt(l.getEmailVerifiedAt())
                .lastLoginAt(l.getLastLoginAt())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
