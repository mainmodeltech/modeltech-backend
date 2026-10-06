package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.response.DomainResponse;
import com.modeltech.datamasteryhub.modules.training.service.DomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
@Tag(name = "Domaines (Public)", description = "Domaines du catalogue — lecture seule")
public class PublicDomainController {

    private final DomainService domainService;

    @GetMapping
    @Operation(summary = "Domaines visibles, dans l'ordre d'affichage")
    public ResponseEntity<List<DomainResponse>> findAll() {
        return ResponseEntity.ok(domainService.findAllVisible());
    }
}
