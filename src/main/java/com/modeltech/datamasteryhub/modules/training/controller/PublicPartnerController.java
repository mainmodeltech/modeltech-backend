package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.response.PartnerResponse;
import com.modeltech.datamasteryhub.modules.training.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
@Tag(name = "Partenaires (Public)", description = "Partenaires formateurs — lecture seule")
public class PublicPartnerController {

    private final PartnerService partnerService;

    @GetMapping
    @Operation(summary = "Partenaires actifs ayant au moins une formation publiée",
            description = "Sans part de revenu ni contact interne.")
    public ResponseEntity<List<PartnerResponse>> findAll() {
        return ResponseEntity.ok(partnerService.findAllPublic());
    }
}
