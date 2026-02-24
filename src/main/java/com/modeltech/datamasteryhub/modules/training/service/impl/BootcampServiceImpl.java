package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSummaryResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.mapper.BootcampMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BootcampServiceImpl implements BootcampService {

    private final BootcampRepository bootcampRepository;
    private final BootcampMapper bootcampMapper;

    @Override
    @Transactional
    public BootcampResponse create(CreateBootcampRequest request) {
        log.info("Création du bootcamp: {}", request.getTitle());
        Bootcamp bootcamp = bootcampMapper.toEntity(request);

        if (request.getFeatured() == null) {
            bootcamp.setFeatured(false);
        }
        if (request.getPublished() == null) {
            bootcamp.setPublished(true);
        }

        Bootcamp saved = bootcampRepository.save(bootcamp);
        log.info("Bootcamp créé avec l'id: {}", saved.getId());
        return bootcampMapper.toResponse(saved);
    }

    @Override
    public Page<BootcampSummaryResponse> findAllForAdmin(Pageable pageable) {
        return bootcampRepository.findAllByIsDeletedFalse(pageable)
                .map(bootcampMapper::toSummaryResponse);
    }

    @Override
    public BootcampResponse findByIdForAdmin(UUID id) {
        Bootcamp bootcamp = bootcampRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp", "id", id));
        return bootcampMapper.toResponse(bootcamp);
    }

    @Override
    @Transactional
    public BootcampResponse update(UUID id, UpdateBootcampRequest request) {
        log.info("Mise à jour du bootcamp: {}", id);
        Bootcamp bootcamp = bootcampRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp", "id", id));

        bootcampMapper.updateEntityFromRequest(request, bootcamp);
        Bootcamp updated = bootcampRepository.save(bootcamp);
        return bootcampMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        log.info("Suppression (soft) du bootcamp: {}", id);
        Bootcamp bootcamp = bootcampRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp", "id", id));

        bootcamp.setDeleted(true);
        bootcamp.setDeletedAt(LocalDateTime.now());
        bootcamp.setDeletedBy("system");
        bootcampRepository.save(bootcamp);
    }

    @Override
    public Page<BootcampSummaryResponse> findAllPublished(Pageable pageable) {
        return bootcampRepository.findAllByPublishedTrueAndIsDeletedFalse(pageable)
                .map(bootcampMapper::toSummaryResponse);
    }

    @Override
    public BootcampResponse findPublishedById(UUID id) {
        Bootcamp bootcamp = bootcampRepository.findByIdAndPublishedTrueAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp", "id", id));
        return bootcampMapper.toResponse(bootcamp);
    }
}
