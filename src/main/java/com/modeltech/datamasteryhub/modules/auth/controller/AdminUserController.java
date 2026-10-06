package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.request.CreateAdminUserRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.UpdateAdminUserRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AdminUserSummaryResponse;
import com.modeltech.datamasteryhub.modules.auth.service.AdminAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Comptes de back-office (SUPER_ADMIN, ADMIN, EDITOR, TRAINER, PARTNER) — réservé aux SUPER_ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Comptes back-office")
public class AdminUserController {

    private final AdminAccountService adminAccountService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserSummaryResponse>>> getAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<AdminUserSummaryResponse> page = adminAccountService.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " compte(s)", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserSummaryResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Compte trouvé", adminAccountService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserSummaryResponse>> create(
            @Valid @RequestBody CreateAdminUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Compte créé, invitation envoyée", adminAccountService.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserSummaryResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateAdminUserRequest req, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Compte mis à jour",
                adminAccountService.update(id, req, authentication.getName())));
    }
}
