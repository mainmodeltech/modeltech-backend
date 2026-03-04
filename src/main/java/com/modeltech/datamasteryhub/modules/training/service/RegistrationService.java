package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RegistrationService {

    /** Creer une inscription (endpoint public) */
    RegistrationResponse register(CreateRegistrationRequest request);

    /** Recuperer toutes les inscriptions pour l'admin (avec filtre optionnel par statut) */
    Page<RegistrationResponse> findAllForAdmin(Pageable pageable, RegistrationStatus status);

    /** Recuperer une inscription par ID pour l'admin */
    RegistrationResponse findByIdForAdmin(UUID id);

    /** Mettre a jour le statut d'une inscription */
    RegistrationResponse updateStatus(UUID id, RegistrationStatus status);

    /** Soft delete */
    void softDelete(UUID id);
}
