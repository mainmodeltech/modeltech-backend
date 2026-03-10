package com.modeltech.datamasteryhub.modules.communication.service;


import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;

import java.util.List;
import java.util.UUID;

public interface TestimonialService {

    // ── Public ──────────────────────────────────────────────────────────────

    /** Retourne la liste des témoignages publiés */
    List<TestimonialResponseDTO> getPublished();

    // ── Admin ───────────────────────────────────────────────────────────────

    /** Retourne tous les témoignages (publiés + non publiés) */
    List<TestimonialResponseDTO> getAll();

    /** Retourne un témoignage par son id */
    TestimonialResponseDTO getById(UUID id);

    /** Crée un nouveau témoignage */
    TestimonialResponseDTO create(TestimonialRequestDTO request);

    /** Met à jour un témoignage existant */
    TestimonialResponseDTO update(UUID id, TestimonialRequestDTO request);

    /** Soft-delete un témoignage */
    void delete(UUID id);

    /** Bascule le statut published d'un témoignage */
    TestimonialResponseDTO togglePublished(UUID id);
}
