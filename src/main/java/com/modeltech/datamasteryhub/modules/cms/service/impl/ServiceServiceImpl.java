package com.modeltech.datamasteryhub.modules.cms.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.cms.dto.request.CreateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.request.UpdateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceResponse;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceSummaryResponse;
import com.modeltech.datamasteryhub.modules.cms.entity.Service;
import com.modeltech.datamasteryhub.modules.cms.mapper.ServiceMapper;
import com.modeltech.datamasteryhub.modules.cms.repository.ServiceRepository;
import com.modeltech.datamasteryhub.modules.cms.service.ServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;
    private final ServiceMapper serviceMapper;

    // ======================== Admin ========================

    @Override
    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        log.info("Creation d'un nouveau service: {}", request.getTitle());
        Service entity = serviceMapper.toEntity(request);
        Service saved = serviceRepository.save(entity);
        return serviceMapper.toResponse(saved);
    }

    @Override
    public Page<ServiceSummaryResponse> findAllForAdmin(Pageable pageable) {
        log.debug("Recuperation de tous les services (admin), page: {}", pageable.getPageNumber());
        return serviceRepository.findAllByIsDeletedFalse(pageable)
                .map(serviceMapper::toSummaryResponse);
    }

    @Override
    public ServiceResponse findByIdForAdmin(UUID id) {
        log.debug("Recuperation du service (admin): {}", id);
        Service entity = serviceRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));
        return serviceMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public ServiceResponse update(UUID id, UpdateServiceRequest request) {
        log.info("Mise a jour du service: {}", id);
        Service entity = serviceRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));

        serviceMapper.updateEntityFromRequest(request, entity);
        Service saved = serviceRepository.save(entity);
        return serviceMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        log.info("Suppression (soft) du service: {}", id);
        Service entity = serviceRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));

        entity.setDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy("system");
        serviceRepository.save(entity);
    }

    // ======================== Public ========================

    @Override
    public List<ServiceSummaryResponse> findAllPublished() {
        log.debug("Recuperation des services publies");
        return serviceRepository.findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc()
                .stream()
                .map(serviceMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceResponse findPublishedById(UUID id) {
        log.debug("Recuperation du service publie: {}", id);
        Service entity = serviceRepository.findByIdAndPublishedTrueAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));
        return serviceMapper.toResponse(entity);
    }
}
