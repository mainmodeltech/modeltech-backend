package com.modeltech.datamasteryhub.modules.cms.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.cms.dto.request.UpsertSiteSettingRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.response.SiteSettingResponse;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.cms.service.SiteContentDefaults;
import com.modeltech.datamasteryhub.modules.cms.service.SiteSettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/site-settings")
@RequiredArgsConstructor
@Tag(name = "Admin - Contenus du site")
public class AdminSiteSettingController {

    private final SiteSettingService siteSettingService;
    private final SiteContentDefaults defaults;

    /** Clés pour lesquelles une valeur par défaut existe (restauration possible). */
    @GetMapping("/defaults")
    public ResponseEntity<ApiResponse<List<String>>> defaultKeys() {
        List<String> keys = List.copyOf(defaults.all().keySet());
        return ResponseEntity.ok(ApiResponse.ok(keys.size() + " valeur(s) par défaut", keys));
    }

    /** Remet un contenu à sa valeur par défaut (celle livrée avec l'application). */
    @PostMapping("/{key}/reset")
    public ResponseEntity<ApiResponse<SiteSettingResponse>> reset(@PathVariable String key) {
        var value = defaults.defaultFor(key)
                .orElseThrow(() -> new ResourceNotFoundException("Valeur par défaut", "clé", key));
        return ResponseEntity.ok(ApiResponse.ok("Contenu restauré", siteSettingService.upsert(key, value)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SiteSettingResponse>>> getAll() {
        List<SiteSettingResponse> all = siteSettingService.findAllForAdmin();
        return ResponseEntity.ok(ApiResponse.ok(all.size() + " contenu(s)", all));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SiteSettingResponse>> upsert(
            @PathVariable String key, @Valid @RequestBody UpsertSiteSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Contenu enregistré", siteSettingService.upsert(key, request.getValue())));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        siteSettingService.delete(key);
        return ResponseEntity.noContent().build();
    }
}
