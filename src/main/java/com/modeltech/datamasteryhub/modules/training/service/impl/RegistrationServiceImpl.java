package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationRepository    registrationRepository;
    private final BootcampRepository        bootcampRepository;
    private final BootcampSessionRepository sessionRepository;
    private final PromoCodeRepository       promoCodeRepository;
    private final RegistrationMapper        registrationMapper;
    private final NotificationService       notificationService;
    private final RecaptchaService          recaptchaService;

    // =========================================================================
    //  INSCRIPTION PUBLIQUE
    // =========================================================================

    /**
     * Enregistre une nouvelle inscription bootcamp.
     *
     * Complexité réduite en déléguant chaque étape à une méthode privée nommée.
     */
    @Override
    @Transactional
    public RegistrationResponse register(CreateRegistrationRequest request) {
        recaptchaService.verify(request.getRecaptchaToken());
        validateProfileFields(request);

        log.info("Nouvelle tentative d'inscription : email={} | sessionId={} | bootcampId={} | profil={}",
                request.getEmail(), request.getSessionId(), request.getBootcampId(), request.getProfile());

        // Vérifier doublon
        if (registrationRepository.existsBySessionIdAndEmailAndBootcampIdAndIsDeletedFalse(
                request.getSessionId(), request.getEmail(), request.getBootcampId())) {
            log.error("Doublon inscription pour email {} et masterclassId {}",
                    request.getEmail(), request.getSessionId());
            throw new IllegalStateException("Cet email est déjà inscrit à cette session de formation.");
        }

        Registration registration = registrationMapper.toEntity(request);
        registration.setStatus(RegistrationStatus.PENDING);

        resolveSession(request, registration);
        resolveBootcampFallback(request, registration);
        applyPromoCode(request, registration);

        // Calcul du montant du (prix - promo)
        registration.setAmountDue(PaymentServiceImpl.calculateAmountDue(registration));

        Registration saved = registrationRepository.save(registration);

        log.info("Nouvelle inscription : {} {} — bootcamp={} | session={} | pays={} | profil={} | promo={}",
                saved.getFirstName(), saved.getLastName(),
                saved.getBootcampTitle(), saved.getSessionName(),
                saved.getCountry(), saved.getProfile(), saved.getPromoCodeUsed());

        notificationService.notifyNewRegistration(saved);
        notificationService.sendRegistrationPendingEmail(saved);

        return registrationMapper.toResponse(saved);
    }

    // =========================================================================
    //  ADMIN
    // =========================================================================

    @Override
    public Page<RegistrationResponse> findAllForAdmin(Pageable pageable, RegistrationStatus status, PaymentStatus paymentStatus) {
        Page<Registration> page;
        if (status != null) {
            page = registrationRepository.findAllByStatusAndIsDeletedFalse(status, pageable);
        } else if (paymentStatus != null) {
            page = registrationRepository.findAllByPaymentStatusAndIsDeletedFalse(paymentStatus, pageable);
        } else {
            page = registrationRepository.findAllByIsDeletedFalse(pageable);
        }
        return page.map(registrationMapper::toResponse);
    }

    @Override
    public RegistrationResponse findByIdForAdmin(UUID id) {
        return registrationRepository.findByIdAndIsDeletedFalse(id)
                .map(registrationMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));
    }

    @Override
    @Transactional
    public RegistrationResponse updateStatus(UUID id, RegistrationStatus newStatus) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));

        RegistrationStatus oldStatus = registration.getStatus();
        registration.setStatus(newStatus);

        updateSessionCapacity(registration, oldStatus, newStatus);

        Registration saved = registrationRepository.save(registration);

        sendConfirmationEmailIfNeeded(saved, oldStatus, newStatus);

        return registrationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));

        if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
            decrementParticipants(registration.getSession());
        }

        registration.setDeleted(true);
        registration.setDeletedAt(LocalDateTime.now());
        registrationRepository.save(registration);
    }

    // =========================================================================
    //  RÉSOLUTION DES ENTITÉS LIÉES — méthodes privées nommées
    // =========================================================================

    /**
     * Résout et attache la session à l'inscription si un sessionId est fourni.
     * Lève un 409 si la session est complète.
     */
    private void resolveSession(CreateRegistrationRequest request, Registration registration) {
        if (request.getSessionId() == null) return;

        Optional<BootcampSession> sessionOpt = sessionRepository.findById(request.getSessionId())
                .filter(s -> !s.isDeleted());

        sessionOpt.ifPresent(session -> {
            requireSessionNotFull(session);
            attachSession(registration, session);
        });
    }

    private void requireSessionNotFull(BootcampSession session) {
        if (Boolean.TRUE.equals(session.getIsFull())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Cette session est complète, inscription impossible.");
        }
    }

    private void attachSession(Registration registration, BootcampSession session) {
        registration.setSession(session);
        registration.setSessionName(session.getSessionName());
        registration.setBootcamp(session.getBootcamp());
        registration.setBootcampTitle(session.getBootcamp().getTitle());
    }

    /**
     * Attache le bootcamp directement si aucune session n'a été résolue.
     * Prend également en compte le titre fourni dans la requête comme dernier recours.
     */
    private void resolveBootcampFallback(CreateRegistrationRequest request, Registration registration) {
        if (registration.getBootcamp() != null) return;

        if (request.getBootcampId() != null) {
            bootcampRepository.findById(request.getBootcampId())
                    .ifPresent(bootcamp -> attachBootcamp(registration, bootcamp));
        }

        if (registration.getBootcampTitle() == null && request.getBootcampTitle() != null) {
            registration.setBootcampTitle(request.getBootcampTitle());
        }
    }

    private void attachBootcamp(Registration registration, Bootcamp bootcamp) {
        registration.setBootcamp(bootcamp);
        registration.setBootcampTitle(bootcamp.getTitle());
    }

    /**
     * Applique le code promo si fourni, valide et non épuisé.
     */
    private void applyPromoCode(CreateRegistrationRequest request, Registration registration) {
        if (isBlank(request.getPromoCode())) return;

        promoCodeRepository
                .findByCodeAndIsActiveTrueAndIsDeletedFalse(request.getPromoCode().trim().toUpperCase())
                .ifPresent(promo -> {
                    if (isPromoExpired(promo) || isPromoExhausted(promo)) return;

                    registration.setPromoCodeId(promo.getId());
                    registration.setPromoCodeUsed(promo.getCode());
                    registration.setDiscountPercent(promo.getDiscountPercent());

                    promo.setUsageCount(promo.getUsageCount() + 1);
                    promoCodeRepository.save(promo);

                    log.info("Code promo {} appliqué : -{}% (parrain: {})",
                            promo.getCode(), promo.getDiscountPercent(), promo.getReferrerName());
                });
    }

    private boolean isPromoExpired(com.modeltech.datamasteryhub.modules.training.entity.PromoCode promo) {
        if (promo.getExpiresAt() == null) return false;
        boolean expired = promo.getExpiresAt().isBefore(LocalDateTime.now());
        if (expired) log.info("Code promo {} expiré, ignoré", promo.getCode());
        return expired;
    }

    private boolean isPromoExhausted(com.modeltech.datamasteryhub.modules.training.entity.PromoCode promo) {
        if (promo.getMaxUses() == null) return false;
        boolean exhausted = promo.getUsageCount() >= promo.getMaxUses();
        if (exhausted) log.info("Code promo {} — max utilisations atteint ({}), ignoré",
                promo.getCode(), promo.getMaxUses());
        return exhausted;
    }

    // =========================================================================
    //  GESTION DU STATUT ET DES CAPACITÉS
    // =========================================================================

    private void updateSessionCapacity(Registration registration,
                                       RegistrationStatus oldStatus,
                                       RegistrationStatus newStatus) {
        if (registration.getSession() == null) return;

        boolean isConfirming   = newStatus == RegistrationStatus.CONFIRMED
                && oldStatus != RegistrationStatus.CONFIRMED;
        boolean isUnconfirming = oldStatus == RegistrationStatus.CONFIRMED
                && (newStatus == RegistrationStatus.CANCELLED
                || newStatus == RegistrationStatus.COMPLETED);

        if (isConfirming)   incrementParticipants(registration.getSession());
        if (isUnconfirming) decrementParticipants(registration.getSession());
    }

    private void sendConfirmationEmailIfNeeded(Registration saved,
                                               RegistrationStatus oldStatus,
                                               RegistrationStatus newStatus) {
        boolean isFirstConfirmation = newStatus == RegistrationStatus.CONFIRMED
                && oldStatus != RegistrationStatus.CONFIRMED;
        if (!isFirstConfirmation) return;

        notificationService.sendRegistrationConfirmedEmail(saved);
        log.info("Email 'place confirmée' déclenché pour {} ({})",
                saved.getFirstName(), saved.getEmail());
    }

    // =========================================================================
    //  VALIDATION DU PROFIL
    // =========================================================================

    private void validateProfileFields(CreateRegistrationRequest request) {
        if (request.getProfile() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le profil est obligatoire.");
        }
        switch (request.getProfile()) {
            case STUDENT      -> validateStudentFields(request);
            case PROFESSIONAL -> validateProfessionalFields(request);
            case ENTREPRENEUR -> { /* company et position sont optionnels */ }
        }
    }

    private void validateStudentFields(CreateRegistrationRequest request) {
        if (isBlank(request.getSchool())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'école ou institution est obligatoire pour un étudiant.");
        }
    }

    private void validateProfessionalFields(CreateRegistrationRequest request) {
        if (isBlank(request.getCompany())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'organisation est obligatoire pour un professionnel.");
        }
        if (isBlank(request.getPosition())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le poste actuel est obligatoire pour un professionnel.");
        }
    }

    // =========================================================================
    //  GESTION DES PLACES SESSION
    // =========================================================================

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

    // =========================================================================
    //  UTILITAIRE
    // =========================================================================

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}