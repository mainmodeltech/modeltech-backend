package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BootcampService {

    // Admin
    BootcampResponse create(CreateBootcampRequest request);
    Page<BootcampSummaryResponse> findAllForAdmin(Pageable pageable);
    BootcampResponse findByIdForAdmin(UUID id);
    BootcampResponse update(UUID id, UpdateBootcampRequest request);
    void softDelete(UUID id);

    // Public
    Page<BootcampSummaryResponse> findAllPublished(Pageable pageable);
    BootcampResponse findPublishedById(UUID id);
}
