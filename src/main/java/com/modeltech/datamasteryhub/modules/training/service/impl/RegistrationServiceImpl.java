package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.mapper.PaymentMapper;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.ManualRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
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
    private final PaymentRepository         paymentRepository;
    private final PaymentMapper             paymentMapper;
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
        if (!recaptchaService.verify(request.getRecaptchaToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérification anti-robot échouée. Veuillez réessayer.");
        }
        Registration saved = persistNew(request, "WEBSITE");

        notificationService.notifyNewRegistration(saved);
        notificationService.sendRegistrationPendingEmail(saved);

        return registrationMapper.toResponse(saved);
    }

    /**
     * Inscription saisie par l'équipe : pas de reCAPTCHA ni d'e-mail automatique (le candidat reçoit
     * le lien de paiement quand la candidature est acceptée).
     */
    @Override
    @Transactional
    public RegistrationResponse createManually(ManualRegistrationRequest request, String actor) {
        CreateRegistrationRequest asPublic = new CreateRegistrationRequest();
        asPublic.setBootcampId(request.getBootcampId());
        asPublic.setSessionId(request.getSessionId());
        asPublic.setBootcampTitle(request.getBootcampTitle());
        asPublic.setPromoCode(request.getPromoCode());
        asPublic.setFirstName(request.getFirstName());
        asPublic.setLastName(request.getLastName());
        asPublic.setEmail(request.getEmail());
        asPublic.setPhone(request.getPhone());
        asPublic.setCountry(request.getCountry());
        asPublic.setProfile(request.getProfile());
        asPublic.setSchool(request.getSchool());
        asPublic.setCompany(request.getCompany());
        asPublic.setPosition(request.getPosition());
        asPublic.setMessage(request.getMessage());

        Registration saved = persistNew(asPublic, "ADMIN");
        log.info("Inscription manuelle créée par {} pour {}", actor, saved.getEmail());
        RegistrationResponse response = registrationMapper.toResponse(saved);
        attachPaymentSummaries(List.of(response));
        return response;
    }

    /** Valide et enregistre une candidature (statut PENDING) : commun au formulaire public et à la saisie manuelle. */
    private Registration persistNew(CreateRegistrationRequest request, String source) {
        validateProfileFields(request);

        Registration registration = registrationMapper.toEntity(request);
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setSource(source);

        resolveSession(request, registration);
        resolveBootcampFallback(request, registration);
        applyPromoCode(request, registration);

        Registration saved = registrationRepository.save(registration);

        log.info("Nouvelle inscription ({}) : {} {} — bootcamp={} | session={} | pays={} | profil={} | promo={}",
                source, saved.getFirstName(), saved.getLastName(),
                saved.getBootcampTitle(), saved.getSessionName(),
                saved.getCountry(), saved.getProfile(), saved.getPromoCodeUsed());
        return saved;
    }

    // =========================================================================
    //  ADMIN
    // =========================================================================

    @Override
    public Page<RegistrationResponse> findAllForAdmin(Pageable pageable, RegistrationStatus status) {
        Page<Registration> page = (status != null)
                ? registrationRepository.findAllByStatusAndIsDeletedFalse(status, pageable)
                : registrationRepository.findAllByIsDeletedFalse(pageable);
        Page<RegistrationResponse> result = page.map(registrationMapper::toResponse);
        attachPaymentSummaries(result.getContent());
        return result;
    }

    @Override
    public RegistrationResponse findByIdForAdmin(UUID id) {
        RegistrationResponse response = registrationRepository.findByIdAndIsDeletedFalse(id)
                .map(registrationMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));
        attachPaymentSummaries(List.of(response));
        return response;
    }

    @Override
    @Transactional
    public RegistrationResponse updateStatus(UUID id, RegistrationStatus newStatus) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));

        RegistrationStatus oldStatus = registration.getStatus();
        registration.setStatus(newStatus);

        updateSessionCapacity(registration, oldStatus, newStatus);

        if (newStatus == RegistrationStatus.CANCELLED || newStatus == RegistrationStatus.REJECTED) {
            cancelOpenPayments(registration.getId());
        }

        Registration saved = registrationRepository.save(registration);

        sendConfirmationEmailIfNeeded(saved, oldStatus, newStatus);

        RegistrationResponse response = registrationMapper.toResponse(saved);
        attachPaymentSummaries(List.of(response));
        return response;
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

    /** Renseigne le résumé des échéances de chaque inscription, en une seule requête. */
    private void attachPaymentSummaries(List<RegistrationResponse> responses) {
        if (responses.isEmpty()) return;
        Map<UUID, List<Payment>> byRegistration = paymentRepository
                .findAllByRegistrationIdInAndIsDeletedFalse(responses.stream().map(RegistrationResponse::getId).toList())
                .stream().collect(Collectors.groupingBy(p -> p.getRegistration().getId()));
        responses.forEach(r -> r.setPaymentSummary(paymentMapper.summarize(byRegistration.getOrDefault(r.getId(), List.of()))));
    }

    /** Une inscription annulée ou refusée n'a plus d'échéance à régler. */
    private void cancelOpenPayments(UUID registrationId) {
        paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(registrationId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.DECLARED)
                .forEach(p -> {
                    p.setStatus(PaymentStatus.CANCELLED);
                    paymentRepository.save(p);
                });
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