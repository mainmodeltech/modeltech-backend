package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.communication.dto.request.SimulatorWaitlistRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator/waitlist")
@RequiredArgsConstructor
@Tag(name = "Formulaires (Public)", description = "Formulaires du site")
public class PublicSimulatorWaitlistController {

    private final ContactMessageService contactMessageService;
    private final IpRateLimiter rateLimiter;

    @PostMapping
    @Operation(summary = "Liste d'attente du simulateur de certification",
            description = "Stockée comme un message de type SIMULATOR_WAITLIST (visible dans les messages admin). Limité par IP.")
    public ResponseEntity<ApiResponse<ContactMessageResponseDTO>> create(
            @Valid @RequestBody SimulatorWaitlistRequestDTO dto,
            HttpServletRequest request) {
        rateLimiter.check(request, "simulator-waitlist");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Merci ! Vous êtes sur la liste d'attente : nous vous prévenons dès l'ouverture.",
                        contactMessageService.saveSimulatorWaitlist(dto)));
    }
}
