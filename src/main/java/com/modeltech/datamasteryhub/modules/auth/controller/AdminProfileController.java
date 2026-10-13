package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.auth.permission.ProfileService;
import com.modeltech.datamasteryhub.modules.auth.permission.ProfileService.PermissionView;
import com.modeltech.datamasteryhub.modules.auth.permission.ProfileService.ProfileInput;
import com.modeltech.datamasteryhub.modules.auth.permission.ProfileService.ProfileView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Profils et droits du back-office — droit « Profils et droits » (direction). */
@RestController
@RequestMapping("/api/v1/admin/profiles")
@RequiredArgsConstructor
@Tag(name = "Admin - Profils et droits")
public class AdminProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProfileView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Profils", profileService.list()));
    }

    /** Catalogue des droits, groupés par domaine (pour l'écran de réglage). */
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionView>>> catalogue() {
        return ResponseEntity.ok(ApiResponse.ok("Droits", profileService.catalogue()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileView>> create(@RequestBody ProfileInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Profil créé", profileService.create(input)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileView>> update(@PathVariable UUID id, @RequestBody ProfileInput input) {
        return ResponseEntity.ok(ApiResponse.ok("Profil mis à jour", profileService.update(id, input)));
    }

    /** Rétablit les droits par défaut d'un profil livré. */
    @PostMapping("/{id}/reset")
    public ResponseEntity<ApiResponse<ProfileView>> reset(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Droits par défaut rétablis", profileService.reset(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        profileService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Profil supprimé", null));
    }
}
