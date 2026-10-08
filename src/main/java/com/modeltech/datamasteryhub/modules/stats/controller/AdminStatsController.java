package com.modeltech.datamasteryhub.modules.stats.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.stats.dto.StatsPayloads;
import com.modeltech.datamasteryhub.modules.stats.service.StatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Tableau de bord d'administration (SUPER_ADMIN et ADMIN : les montants sont sensibles). */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@Tag(name = "Admin - Statistiques")
public class AdminStatsController {

    private final StatsService statsService;

    /** Ce qui attend l'équipe (pastilles du menu) : léger, à interroger souvent. */
    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<StatsPayloads.Actions>> actions() {
        return ResponseEntity.ok(ApiResponse.ok("À traiter", statsService.actions()));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<StatsPayloads.Overview>> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Indicateurs", statsService.overview(from, to)));
    }
}
