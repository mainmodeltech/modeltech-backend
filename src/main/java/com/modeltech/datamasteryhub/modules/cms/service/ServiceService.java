package com.modeltech.datamasteryhub.modules.cms.service;

import com.modeltech.datamasteryhub.modules.cms.dto.request.CreateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.request.UpdateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceResponse;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ServiceService {

    // ======================== Admin ========================

    ServiceResponse create(CreateServiceRequest request);

    Page<ServiceSummaryResponse> findAllForAdmin(Pageable pageable);

    ServiceResponse findByIdForAdmin(UUID id);

    ServiceResponse update(UUID id, UpdateServiceRequest request);

    void softDelete(UUID id);

    // ======================== Public ========================

    /** Liste plate triee par displayOrder (pas de pagination) */
    List<ServiceSummaryResponse> findAllPublished();

    ServiceResponse findPublishedById(UUID id);
}
