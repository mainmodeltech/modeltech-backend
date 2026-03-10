package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.response.SessionAvailabilityResponse;
import java.util.UUID;

public interface BootcampSessionService {
    // ... méthodes existantes

    /**
     * Récupère la disponibilité d'une session (places restantes)
     * @param sessionId ID de la session
     * @return Disponibilité avec places restantes et couleur recommandée
     */
    SessionAvailabilityResponse getSessionAvailability(UUID sessionId);
}
