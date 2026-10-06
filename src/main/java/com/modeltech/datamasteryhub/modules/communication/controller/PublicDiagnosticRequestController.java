package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.communication.dto.request.DiagnosticRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diagnostic-requests")
@RequiredArgsConstructor
@Tag(name = "Formulaires (Public)", description = "Formulaires du site")
public class PublicDiagnosticRequestController {

    private final ContactMessageService contactMessageService;
    private final IpRateLimiter rateLimiter;

    @PostMapping
    @Operation(summary = "Demande de diagnostic gratuit d'une entreprise",
            description = "Stockée comme un message de type DIAGNOSTIC (visible dans les messages admin). Limité par IP.")
    public ResponseEntity<ApiResponse<ContactMessageResponseDTO>> create(
            @Valid @RequestBody DiagnosticRequestDTO dto,
            HttpServletRequest request) {
        rateLimiter.check(request, "diagnostic");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Demande envoyée. Nous vous recontactons pour planifier votre diagnostic.",
                        contactMessageService.saveDiagnosticRequest(dto)));
    }
}
