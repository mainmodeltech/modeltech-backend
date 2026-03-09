package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.training.dto.response.SessionAvailabilityResponse;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import com.modeltech.datamasteryhub.modules.training.service.BootcampSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BootcampSessionServiceImpl implements BootcampSessionService {

    private final BootcampSessionRepository bootcampSessionRepository;
    private final RegistrationRepository registrationRepository;

    @Override
    public SessionAvailabilityResponse getSessionAvailability(UUID sessionId) {
        log.info("Récupération de la disponibilité pour la session: {}", sessionId);

        // Récupérer la session
        BootcampSession session = bootcampSessionRepository.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session de bootcamp", "id", sessionId));

        // Vérifier si la session est publiée
        if (Boolean.FALSE.equals(session.getPublished())) {
            throw new ResourceNotFoundException("Session non publiée: " + sessionId);
        }

        // Compter les inscriptions confirmées pour cette session
        int confirmedRegistrations = registrationRepository.countBySession_IdAndStatusAndIsDeletedFalse(
                sessionId, RegistrationStatus.CONFIRMED);

        // Mettre à jour currentParticipants si nécessaire
        if (session.getCurrentParticipants() != confirmedRegistrations) {
            log.info("Mise à jour du compteur de participants: {} -> {}",
                    session.getCurrentParticipants(), confirmedRegistrations);
            session.setCurrentParticipants(confirmedRegistrations);

            // Mettre à jour isFull
            boolean isFull = session.getMaxParticipants() != null &&
                    confirmedRegistrations >= session.getMaxParticipants();
            session.setIsFull(isFull);

            bootcampSessionRepository.save(session);
        }

        // Calculer les places restantes
        Integer maxParticipants = session.getMaxParticipants();
        Integer currentParticipants = session.getCurrentParticipants();

        Integer remainingSpots = null;
        Integer remainingPercentage = null;
        String displayColor;
        String displayMessage;
        boolean isOpenForRegistration = false;

        // Vérifier si la session accepte les inscriptions
        LocalDate today = LocalDate.now();
        boolean isRegistrationOpen = session.getStatus() == SessionStatus.OPEN ||
                session.getStatus() == SessionStatus.UPCOMING;
        boolean isDeadlinePassed = session.getRegistrationDeadline() != null &&
                session.getRegistrationDeadline().isBefore(today);
        boolean isExpired = session.getEndDate() != null &&
                session.getEndDate().isBefore(today);

        isOpenForRegistration = isRegistrationOpen && !isDeadlinePassed && !isExpired && !session.getIsFull();

        if (maxParticipants == null) {
            // Capacité illimitée
            remainingSpots = null;
            remainingPercentage = null;
            displayColor = "green";
            displayMessage = "Places illimitées";
        } else {
            remainingSpots = Math.max(0, maxParticipants - currentParticipants);
            remainingPercentage = (remainingSpots * 100) / maxParticipants;

            // Déterminer la couleur selon le pourcentage de places restantes
            if (remainingPercentage <= 0) {
                displayColor = "red";
                displayMessage = "Complet";
            } else if (remainingPercentage < 25) {
                displayColor = "red";
                displayMessage = "Plus que " + remainingSpots + " place" + (remainingSpots > 1 ? "s" : "");
            } else if (remainingPercentage < 50) {
                displayColor = "yellow";
                displayMessage = "Plus que " + remainingSpots + " places (moins de 50%)";
            } else {
                displayColor = "green";
                displayMessage = remainingSpots + " places disponibles";
            }
        }

        // Construire la réponse
        return SessionAvailabilityResponse.builder()
                .sessionId(session.getId())
                .sessionName(session.getSessionName())
                .bootcampId(session.getBootcamp().getId())
                .bootcampTitle(session.getBootcamp().getTitle())
                .maxParticipants(maxParticipants)
                .currentParticipants(currentParticipants)
                .remainingSpots(remainingSpots)
                .remainingPercentage(remainingPercentage)
                .displayColor(displayColor)
                .displayMessage(displayMessage)
                .isOpenForRegistration(isOpenForRegistration)
                .isFull(session.getIsFull())
                .build();
    }
}
