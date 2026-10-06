package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.common.util.SlugUtils;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminDomainResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.DomainResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.mapper.DomainMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.DomainRepository;
import com.modeltech.datamasteryhub.modules.training.service.DomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DomainServiceImpl implements DomainService {

    private final DomainRepository domainRepository;
    private final BootcampRepository bootcampRepository;
    private final DomainMapper domainMapper;

    // ── Public ──────────────────────────────────────────────────────

    @Override
    public List<DomainResponse> findAllVisible() {
        return domainMapper.toResponseList(
                domainRepository.findAllByVisibleTrueAndIsDeletedFalseOrderByDisplayOrderAscNameAsc());
    }

    // ── Admin ───────────────────────────────────────────────────────

    @Override
    public Page<AdminDomainResponse> findAllForAdmin(Pageable pageable) {
        return domainRepository.findAllByIsDeletedFalse(pageable).map(domainMapper::toAdminResponse);
    }

    @Override
    public AdminDomainResponse findByIdForAdmin(UUID id) {
        return domainMapper.toAdminResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public AdminDomainResponse create(CreateDomainRequest request) {
        log.info("Création domaine: {}", request.getName());
        Domain domain = domainMapper.toEntity(request);
        domain.setSlug(hasText(request.getSlug())
                ? requireFreeSlug(request.getSlug(), domainRepository.existsBySlug(request.getSlug()))
                : SlugUtils.unique(request.getName(), "domaine", domainRepository::existsBySlug));
        return domainMapper.toAdminResponse(domainRepository.save(domain));
    }

    @Override
    @Transactional
    public AdminDomainResponse update(UUID id, UpdateDomainRequest request) {
        Domain domain = getOrThrow(id);
        if (hasText(request.getSlug()) && !request.getSlug().equals(domain.getSlug())) {
            domain.setSlug(requireFreeSlug(request.getSlug(),
                    domainRepository.existsBySlugAndIdNot(request.getSlug(), id)));
        }
        domainMapper.updateEntity(request, domain);
        return domainMapper.toAdminResponse(domainRepository.save(domain));
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Domain domain = getOrThrow(id);
        if (bootcampRepository.existsByDomainIdAndIsDeletedFalse(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce domaine contient des formations : déplacez-les ou supprimez-les d'abord.");
        }
        domain.setDeleted(true);
        domain.setDeletedAt(LocalDateTime.now());
        domain.setDeletedBy("system");
        domainRepository.save(domain);
    }

    // ── Privé ───────────────────────────────────────────────────────

    private Domain getOrThrow(UUID id) {
        return domainRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Domaine", "id", id));
    }

    private String requireFreeSlug(String slug, boolean alreadyUsed) {
        if (alreadyUsed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce slug est déjà utilisé : " + slug);
        }
        return slug;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
