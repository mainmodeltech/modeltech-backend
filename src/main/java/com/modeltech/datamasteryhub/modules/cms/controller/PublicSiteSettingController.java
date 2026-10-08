package com.modeltech.datamasteryhub.modules.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.cms.service.SiteSettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/site-settings")
@RequiredArgsConstructor
@Tag(name = "Public - Contenus du site")
public class PublicSiteSettingController {

    private final SiteSettingService siteSettingService;

    /** Tous les contenus éditables du site, sous forme {@code {clé: valeur}}. Une clé absente = bloc à masquer. */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, JsonNode>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Contenus du site", siteSettingService.findAllPublic()));
    }
}
