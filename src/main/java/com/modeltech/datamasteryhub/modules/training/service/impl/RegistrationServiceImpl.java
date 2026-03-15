package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.mapper.RegistrationMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PromoCodeRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationRepository     registrationRepository;
    private final BootcampRepository         bootcampRepository;
    private final BootcampSessionRepository  sessionRepository;
    private final PromoCodeRepository        promoCodeRepository;
    private final RegistrationMapper         registrationMapper;
    private final NotificationService        notificationService;
    private final RecaptchaService recaptchaService;   // ← même bean que la masterclass

    // =========================================================================
    //  INSCRIPTION PUBLIQUE
    // =========================================================================

    @Override
    @Transactional
    public RegistrationResponse register(CreateRegistrationRequest request) {

        // ── 1. Vérification reCAPTCHA ─────────────────────────────────────────
        // Même appel que MasterclassRegistrationServiceImpl
        recaptchaService.verify(request.getRecaptchaToken());

        // ── 2. Validation conditionnelle selon le profil ──────────────────────
        validateProfileFields(request);

        // ── 3. Mapping de base (country, profile, school, company, position…) ─
        Registration registration = registrationMapper.toEntity(request);
        registration.setStatus(RegistrationStatus.PENDING);

        // ── 4. Résolution de la session ───────────────────────────────────────
        if (request.getSessionId() != null) {
            BootcampSession session = sessionRepository.findById(request.getSessionId())
                    .filter(s -> !s.isDeleted())
                    .orElse(null);

            if (session != null) {
                if (Boolean.TRUE.equals(session.getIsFull())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Cette session est complète, inscription impossible.");
                }
                registration.setSession(session);
                registration.setSessionName(session.getSessionName());
                registration.setBootcamp(session.getBootcamp());
                registration.setBootcampTitle(session.getBootcamp().getTitle());
            }
        }

        // ── 5. Bootcamp fallback (si pas de session) ──────────────────────────
        if (registration.getBootcamp() == null && request.getBootcampId() != null) {
            bootcampRepository.findById(request.getBootcampId()).ifPresent(bootcamp -> {
                registration.setBootcamp(bootcamp);
                registration.setBootcampTitle(bootcamp.getTitle());
            });
        }

        if (registration.getBootcampTitle() == null && request.getBootcampTitle() != null) {
            registration.setBootcampTitle(request.getBootcampTitle());
        }

        // ── 6. Code promo ─────────────────────────────────────────────────────
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoCodeRepository
                    .findByCodeAndIsActiveTrueAndIsDeletedFalse(request.getPromoCode().trim().toUpperCase())
                    .ifPresent(promo -> {
                        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(LocalDateTime.now())) {
                            log.info("Code promo {} expiré, ignoré", promo.getCode());
                            return;
                        }
                        if (promo.getMaxUses() != null && promo.getUsageCount() >= promo.getMaxUses()) {
                            log.info("Code promo {} — max utilisations atteint ({}), ignoré",
                                    promo.getCode(), promo.getMaxUses());
                            return;
                        }
                        registration.setPromoCodeId(promo.getId());
                        registration.setPromoCodeUsed(promo.getCode());
                        registration.setDiscountPercent(promo.getDiscountPercent());

                        promo.setUsageCount(promo.getUsageCount() + 1);
                        promoCodeRepository.save(promo);

                        log.info("Code promo {} appliqué : -{}% (parrain: {})",
                                promo.getCode(), promo.getDiscountPercent(), promo.getReferrerName());
                    });
        }

        // ── 7. Sauvegarde ─────────────────────────────────────────────────────
        Registration saved = registrationRepository.save(registration);

        log.info("Nouvelle inscription créée : {} {} — bootcamp={} | session={} | pays={} | profil={} | promo={}",
                saved.getFirstName(), saved.getLastName(),
                saved.getBootcampTitle(), saved.getSessionName(),
                saved.getCountry(), saved.getProfile(), saved.getPromoCodeUsed());

        // ── 8. Notifications asynchrones (Slack + Email) ──────────────────────
        notificationService.notifyNewRegistration(saved);

        return registrationMapper.toResponse(saved);
    }

    // =========================================================================
    //  ADMIN
    // =========================================================================

    @Override
    public Page<RegistrationResponse> findAllForAdmin(Pageable pageable, RegistrationStatus status) {
        Page<Registration> page = (status != null)
                ? registrationRepository.findAllByStatusAndIsDeletedFalse(status, pageable)
                : registrationRepository.findAllByIsDeletedFalse(pageable);
        return page.map(registrationMapper::toResponse);
    }

    @Override
    public RegistrationResponse findByIdForAdmin(UUID id) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));
        return registrationMapper.toResponse(registration);
    }

    @Override
    @Transactional
    public RegistrationResponse updateStatus(UUID id, RegistrationStatus newStatus) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));

        RegistrationStatus oldStatus = registration.getStatus();
        registration.setStatus(newStatus);

        if (registration.getSession() != null) {
            if (newStatus == RegistrationStatus.CONFIRMED && oldStatus != RegistrationStatus.CONFIRMED) {
                incrementParticipants(registration.getSession());
            } else if (oldStatus == RegistrationStatus.CONFIRMED &&
                    (newStatus == RegistrationStatus.CANCELLED || newStatus == RegistrationStatus.COMPLETED)) {
                decrementParticipants(registration.getSession());
            }
        }

        return registrationMapper.toResponse(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));

        if (registration.getStatus() == RegistrationStatus.CONFIRMED && registration.getSession() != null) {
            decrementParticipants(registration.getSession());
        }

        registration.setDeleted(true);
        registration.setDeletedAt(LocalDateTime.now());
        registrationRepository.save(registration);
    }

    // =========================================================================
    //  HELPERS PRIVÉS
    // =========================================================================

    /**
     * Validation conditionnelle selon le profil de l'inscrit.
     *
     * STUDENT      → school est obligatoire
     * PROFESSIONAL → company ET position sont obligatoires
     * ENTREPRENEUR → aucune contrainte supplémentaire
     */
    private void validateProfileFields(CreateRegistrationRequest request) {
        if (request.getProfile() == null) {
            // Ne devrait pas arriver grâce à @NotNull sur le DTO, mais sécurité supplémentaire
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le profil est obligatoire.");
        }

        switch (request.getProfile()) {
            case STUDENT -> {
                if (isBlank(request.getSchool())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "L'école ou institution est obligatoire pour un étudiant.");
                }
            }
            case PROFESSIONAL -> {
                if (isBlank(request.getCompany())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "L'organisation est obligatoire pour un professionnel.");
                }
                if (isBlank(request.getPosition())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Le poste actuel est obligatoire pour un professionnel.");
                }
            }
            case ENTREPRENEUR -> {
                // company et position sont optionnels — aucune contrainte
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void incrementParticipants(BootcampSession session) {
        if (session == null) return;
        session.setCurrentParticipants(session.getCurrentParticipants() + 1);
        session.setIsFull(session.getCurrentParticipants() >= session.getMaxParticipants());
        sessionRepository.save(session);
        log.info("Session {} : {}/{} participants",
                session.getSessionName(), session.getCurrentParticipants(), session.getMaxParticipants());
    }

    private void decrementParticipants(BootcampSession session) {
        if (session == null) return;
        session.setCurrentParticipants(Math.max(0, session.getCurrentParticipants() - 1));
        session.setIsFull(false);
        sessionRepository.save(session);
        log.info("Session {} : {}/{} participants (place libérée)",
                session.getSessionName(), session.getCurrentParticipants(), session.getMaxParticipants());
    }
}