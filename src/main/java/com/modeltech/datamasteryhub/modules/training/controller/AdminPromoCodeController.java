package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PromoCodeResponse;
import com.modeltech.datamasteryhub.modules.training.service.PromoCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/promo-codes")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final PromoCodeService promoCodeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeResponse create(@Valid @RequestBody CreatePromoCodeRequest request) {
        return promoCodeService.create(request);
    }

    @GetMapping
    public Page<PromoCodeResponse> getAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return promoCodeService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public PromoCodeResponse getById(@PathVariable UUID id) {
        return promoCodeService.findById(id);
    }

    @PutMapping("/{id}")
    public PromoCodeResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePromoCodeRequest request
    ) {
        return promoCodeService.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    public PromoCodeResponse toggleActive(@PathVariable UUID id) {
        return promoCodeService.toggleActive(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        promoCodeService.softDelete(id);
    }
}
