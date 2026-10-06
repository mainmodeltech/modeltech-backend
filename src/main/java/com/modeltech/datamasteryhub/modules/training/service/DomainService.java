package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminDomainResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.DomainResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DomainService {

    // Public
    List<DomainResponse> findAllVisible();

    // Admin
    Page<AdminDomainResponse> findAllForAdmin(Pageable pageable);
    AdminDomainResponse findByIdForAdmin(UUID id);
    AdminDomainResponse create(CreateDomainRequest request);
    AdminDomainResponse update(UUID id, UpdateDomainRequest request);
    void softDelete(UUID id);
}
