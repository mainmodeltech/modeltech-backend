package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPartnerResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.PartnerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface PartnerService {

    // Public : uniquement les partenaires actifs ayant au moins une formation publiée
    List<PartnerResponse> findAllPublic();

    // Admin
    Page<AdminPartnerResponse> findAllForAdmin(Pageable pageable);
    AdminPartnerResponse findByIdForAdmin(UUID id);
    AdminPartnerResponse create(CreatePartnerRequest request);
    AdminPartnerResponse update(UUID id, UpdatePartnerRequest request);
    AdminPartnerResponse uploadLogo(UUID id, MultipartFile file);
    void softDelete(UUID id);
}
