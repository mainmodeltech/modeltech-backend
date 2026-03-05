package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.PromoCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final BootcampRepository bootcampRepository;
    private final BootcampSessionRepository sessionRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final RegistrationMapper registrationMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RegistrationResponse register(CreateRegistrationRequest request) {
        Registration registration = registrationMapper.toEntity(request);
        registration.setStatus(RegistrationStatus.PENDING);

        // ── Session ────────────────────────────────────────────────
        if (request.getSessionId() != null) {
            BootcampSession session = sessionRepository.findById(request.getSessionId())
                    .filter(s -> !s.isDeleted())
                    .orElse(null);

            if (session != null) {
                if (Boolean.TRUE.equals(session.getIsFull())) {
                    throw new IllegalStateException("Cette session est complete, inscription impossible.");
                }

                registration.setSession(session);
                registration.setSessionName(session.getSessionName());

                // Auto-remplir le bootcamp depuis la session
                registration.setBootcamp(session.getBootcamp());
                registration.setBootcampTitle(session.getBootcamp().getTitle());
            }
        }

        // ── Bootcamp (fallback si pas de session) ──────────────────
        if (registration.getBootcamp() == null && request.getBootcampId() != null) {
            Bootcamp bootcamp = bootcampRepository.findById(request.getBootcampId())
                    .orElse(null);
            if (bootcamp != null) {
                registration.setBootcamp(bootcamp);
                registration.setBootcampTitle(bootcamp.getTitle());
            }
        }

        if (registration.getBootcampTitle() == null && request.getBootcampTitle() != null) {
            registration.setBootcampTitle(request.getBootcampTitle());
        }

        // ── Code promo ─────────────────────────────────────────────
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoCodeRepository.findByCodeAndIsActiveTrueAndIsDeletedFalse(request.getPromoCode().trim().toUpperCase())
                    .ifPresent(promo -> {
                        // Verifier expiration
                        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(LocalDateTime.now())) {
                            log.info("Code promo {} expire, ignore", promo.getCode());
                            return;
                        }
                        // Verifier max utilisations
                        if (promo.getMaxUses() != null && promo.getUsageCount() >= promo.getMaxUses()) {
                            log.info("Code promo {} a atteint le max d'utilisations ({}), ignore",
                                    promo.getCode(), promo.getMaxUses());
                            return;
                        }

                        // Appliquer le code promo
                        registration.setPromoCodeId(promo.getId());
                        registration.setPromoCodeUsed(promo.getCode());
                        registration.setDiscountPercent(promo.getDiscountPercent());

                        // Incrementer le compteur d'utilisation
                        promo.setUsageCount(promo.getUsageCount() + 1);
                        promoCodeRepository.save(promo);

                        log.info("Code promo {} applique : -{}% (parrain: {})",
                                promo.getCode(), promo.getDiscountPercent(), promo.getReferrerName());
                    });
        }

        Registration saved = registrationRepository.save(registration);

        log.info("Nouvelle inscription creee : {} {} pour {} (session: {}, promo: {})",
                saved.getFirstName(), saved.getLastName(), saved.getBootcampTitle(),
                saved.getSessionName(), saved.getPromoCodeUsed());

        // Notifications asynchrones (Slack + Email)
        notificationService.notifyNewRegistration(saved);

        return registrationMapper.toResponse(saved);
    }

    @Override
    public Page<RegistrationResponse> findAllForAdmin(Pageable pageable, RegistrationStatus status) {
        Page<Registration> page;
        if (status != null) {
            page = registrationRepository.findAllByStatusAndIsDeletedFalse(status, pageable);
        } else {
            page = registrationRepository.findAllByIsDeletedFalse(pageable);
        }
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

        // ── Gestion des places sur la session ──────────────────────
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

        // Si l'inscription etait confirmee, on libere la place
        if (registration.getStatus() == RegistrationStatus.CONFIRMED && registration.getSession() != null) {
            decrementParticipants(registration.getSession());
        }

        registration.setDeleted(true);
        registration.setDeletedAt(LocalDateTime.now());
        registrationRepository.save(registration);
    }

    // ── Gestion des places ────────────────────────────────────────

    private void incrementParticipants(BootcampSession session) {
        if (session == null) return;
        session.setCurrentParticipants(session.getCurrentParticipants() + 1);
        session.setIsFull(session.getCurrentParticipants() >= session.getMaxParticipants());
        sessionRepository.save(session);
        log.info("Session {} : {} / {} participants",
                session.getSessionName(), session.getCurrentParticipants(), session.getMaxParticipants());
    }

    private void decrementParticipants(BootcampSession session) {
        if (session == null) return;
        session.setCurrentParticipants(Math.max(0, session.getCurrentParticipants() - 1));
        session.setIsFull(false);
        sessionRepository.save(session);
        log.info("Session {} : {} / {} participants (place liberee)",
                session.getSessionName(), session.getCurrentParticipants(), session.getMaxParticipants());
    }
}
