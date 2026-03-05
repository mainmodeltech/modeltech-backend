package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PromoCodeResponse;
import com.modeltech.datamasteryhub.modules.training.entity.PromoCode;
import com.modeltech.datamasteryhub.modules.training.mapper.PromoCodeMapper;
import com.modeltech.datamasteryhub.modules.training.repository.PromoCodeRepository;
import com.modeltech.datamasteryhub.modules.training.service.PromoCodeService;
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
@Slf4j
@Transactional(readOnly = true)
public class PromoCodeServiceImpl implements PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeMapper promoCodeMapper;

    @Override
    @Transactional
    public PromoCodeResponse create(CreatePromoCodeRequest request) {
        PromoCode promoCode = promoCodeMapper.toEntity(request);
        promoCode.setCode(request.getCode().trim().toUpperCase());
        promoCode.setIsActive(true);
        promoCode.setUsageCount(0);
        return promoCodeMapper.toResponse(promoCodeRepository.save(promoCode));
    }

    @Override
    @Transactional
    public PromoCodeResponse update(UUID id, UpdatePromoCodeRequest request) {
        PromoCode promoCode = getOrThrow(id);
        promoCodeMapper.updateEntity(request, promoCode);
        if (request.getCode() != null) {
            promoCode.setCode(request.getCode().trim().toUpperCase());
        }
        return promoCodeMapper.toResponse(promoCodeRepository.save(promoCode));
    }

    @Override
    public Page<PromoCodeResponse> findAll(Pageable pageable) {
        return promoCodeRepository.findAllByIsDeletedFalse(pageable)
                .map(promoCodeMapper::toResponse);
    }

    @Override
    public PromoCodeResponse findById(UUID id) {
        return promoCodeMapper.toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public PromoCodeResponse toggleActive(UUID id) {
        PromoCode promoCode = getOrThrow(id);
        promoCode.setIsActive(!promoCode.getIsActive());
        return promoCodeMapper.toResponse(promoCodeRepository.save(promoCode));
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        PromoCode promoCode = getOrThrow(id);
        promoCode.setDeleted(true);
        promoCode.setDeletedAt(LocalDateTime.now());
        promoCode.setIsActive(false);
        promoCodeRepository.save(promoCode);
    }

    @Override
    public PromoCodeResponse validateCode(String code) {
        if (code == null || code.isBlank()) return null;

        return promoCodeRepository.findByCodeAndIsActiveTrueAndIsDeletedFalse(code.trim().toUpperCase())
                .filter(promo -> {
                    // Non expire
                    if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return false;
                    }
                    // Max pas atteint
                    if (promo.getMaxUses() != null && promo.getUsageCount() >= promo.getMaxUses()) {
                        return false;
                    }
                    return true;
                })
                .map(promoCodeMapper::toResponse)
                .orElse(null);
    }

    private PromoCode getOrThrow(UUID id) {
        return promoCodeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Code promo", "id", id));
    }
}
