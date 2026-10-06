package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.request.CreateLearnerRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.LearnerResponse;
import com.modeltech.datamasteryhub.modules.auth.service.LearnerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/learners")
@RequiredArgsConstructor
@Tag(name = "Admin - Apprenants")
public class AdminLearnerController {

    private final LearnerService learnerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LearnerResponse>>> getAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<LearnerResponse> page = learnerService.findAllForAdmin(pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " apprenant(s)", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LearnerResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Apprenant trouvé", learnerService.findByIdForAdmin(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LearnerResponse>> create(@Valid @RequestBody CreateLearnerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Compte apprenant créé, invitation envoyée", learnerService.create(req)));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<LearnerResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Compte activé", learnerService.setActive(id, true)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<LearnerResponse>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Compte désactivé", learnerService.setActive(id, false)));
    }

    @PostMapping("/{id}/resend-invitation")
    public ResponseEntity<ApiResponse<Void>> resendInvitation(@PathVariable UUID id) {
        learnerService.resendInvitation(id);
        return ResponseEntity.ok(ApiResponse.ok("Invitation renvoyée", null));
    }
}
