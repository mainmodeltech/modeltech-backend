package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.common.util.SlugUtils;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPartnerResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.PartnerResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.mapper.PartnerMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import com.modeltech.datamasteryhub.modules.training.service.PartnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PartnerServiceImpl implements PartnerService {

    private final PartnerRepository partnerRepository;
    private final BootcampRepository bootcampRepository;
    private final PartnerMapper partnerMapper;
    private final StorageService storageService;

    // ── Public ──────────────────────────────────────────────────────

    @Override
    public List<PartnerResponse> findAllPublic() {
        return partnerMapper.toResponseList(partnerRepository.findAllPublic());
    }

    // ── Admin ───────────────────────────────────────────────────────

    @Override
    public Page<AdminPartnerResponse> findAllForAdmin(Pageable pageable) {
        return partnerRepository.findAllByIsDeletedFalse(pageable).map(partnerMapper::toAdminResponse);
    }

    @Override
    public AdminPartnerResponse findByIdForAdmin(UUID id) {
        return partnerMapper.toAdminResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public AdminPartnerResponse create(CreatePartnerRequest request) {
        log.info("Création partenaire: {}", request.getName());
        Partner partner = partnerMapper.toEntity(request);
        partner.setSlug(hasText(request.getSlug())
                ? requireFreeSlug(request.getSlug(), partnerRepository.existsBySlug(request.getSlug()))
                : SlugUtils.unique(request.getName(), "partenaire", partnerRepository::existsBySlug));
        return partnerMapper.toAdminResponse(partnerRepository.save(partner));
    }

    @Override
    @Transactional
    public AdminPartnerResponse update(UUID id, UpdatePartnerRequest request) {
        Partner partner = getOrThrow(id);
        if (hasText(request.getSlug()) && !request.getSlug().equals(partner.getSlug())) {
            partner.setSlug(requireFreeSlug(request.getSlug(),
                    partnerRepository.existsBySlugAndIdNot(request.getSlug(), id)));
        }
        partnerMapper.updateEntity(request, partner);
        return partnerMapper.toAdminResponse(partnerRepository.save(partner));
    }

    @Override
    @Transactional
    public AdminPartnerResponse uploadLogo(UUID id, MultipartFile file) {
        Partner partner = getOrThrow(id);
        String previousKey = partner.getLogoObjectKey();

        StorageService.UploadResult result = storageService.upload(file, "partners");
        partner.setLogoUrl(result.url());
        partner.setLogoObjectKey(result.objectKey());
        Partner saved = partnerRepository.save(partner);

        if (previousKey != null && !previousKey.isBlank()) {
            try {
                storageService.delete(previousKey);
            } catch (RuntimeException e) {
                // L'ancien logo orphelin n'empêche pas la mise à jour
                log.warn("Suppression de l'ancien logo partenaire impossible ({}): {}", previousKey, e.getMessage());
            }
        }
        return partnerMapper.toAdminResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Partner partner = getOrThrow(id);
        if (bootcampRepository.existsByPartnerIdAndIsDeletedFalse(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce partenaire dispense des formations : réattribuez-les d'abord.");
        }
        partner.setDeleted(true);
        partner.setDeletedAt(LocalDateTime.now());
        partner.setDeletedBy("system");
        partnerRepository.save(partner);
    }

    // ── Privé ───────────────────────────────────────────────────────

    private Partner getOrThrow(UUID id) {
        return partnerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partenaire", "id", id));
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
