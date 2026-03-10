package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PromoCodeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromoCodeService {

    PromoCodeResponse create(CreatePromoCodeRequest request);

    PromoCodeResponse update(UUID id, UpdatePromoCodeRequest request);

    Page<PromoCodeResponse> findAll(Pageable pageable);

    PromoCodeResponse findById(UUID id);

    PromoCodeResponse toggleActive(UUID id);

    void softDelete(UUID id);

    /** Valider un code cote public (retourne null si invalide) */
    PromoCodeResponse validateCode(String code);
}
