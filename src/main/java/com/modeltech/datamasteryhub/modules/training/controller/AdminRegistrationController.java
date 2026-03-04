package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateRegistrationStatusRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/registrations")
@RequiredArgsConstructor
public class AdminRegistrationController {

    private final RegistrationService registrationService;

    @GetMapping
    public Page<RegistrationResponse> getAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @RequestParam(required = false) RegistrationStatus status
    ) {
        return registrationService.findAllForAdmin(pageable, status);
    }

    @GetMapping("/{id}")
    public RegistrationResponse getById(@PathVariable UUID id) {
        return registrationService.findByIdForAdmin(id);
    }

    @PatchMapping("/{id}/status")
    public RegistrationResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRegistrationStatusRequest request
    ) {
        return registrationService.updateStatus(id, request.getStatus());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        registrationService.softDelete(id);
    }
}
