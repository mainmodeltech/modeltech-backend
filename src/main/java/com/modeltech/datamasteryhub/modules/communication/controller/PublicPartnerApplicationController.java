package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.communication.dto.request.PartnerApplicationRequestDTO;
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
@RequestMapping("/api/v1/partner-applications")
@RequiredArgsConstructor
@Tag(name = "Formulaires (Public)", description = "Formulaires du site")
public class PublicPartnerApplicationController {

    private final ContactMessageService contactMessageService;
    private final IpRateLimiter rateLimiter;

    @PostMapping
    @Operation(summary = "Candidature d'un formateur partenaire",
            description = "Stockée comme un message de type PARTNER_APPLICATION (visible dans les messages admin). Limité par IP.")
    public ResponseEntity<ApiResponse<ContactMessageResponseDTO>> create(
            @Valid @RequestBody PartnerApplicationRequestDTO dto,
            HttpServletRequest request) {
        rateLimiter.check(request, "partner-application");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Candidature envoyée. Nous étudions chaque proposition et revenons vers vous rapidement.",
                        contactMessageService.savePartnerApplication(dto)));
    }
}
