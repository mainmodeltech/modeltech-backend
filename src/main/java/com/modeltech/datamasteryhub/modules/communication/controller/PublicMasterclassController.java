package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.request.MasterclassRegistrationRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.MasterclassRegistrationResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.MasterclassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Public  : POST /api/v1/masterclass/register
 */

@RequestMapping("/api/v1/masterclass")
@RestController
@RequiredArgsConstructor
public class PublicMasterclassController {

    private final MasterclassService masterclassService;

    // ── Public ─────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<MasterclassRegistrationResponseDTO>> register(
            @Valid @RequestBody MasterclassRegistrationRequestDTO request) {
        try {
            MasterclassRegistrationResponseDTO data = masterclassService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(
                            "Inscription confirmée ! Vérifiez votre email pour le lien Google Meet.",
                            data));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
