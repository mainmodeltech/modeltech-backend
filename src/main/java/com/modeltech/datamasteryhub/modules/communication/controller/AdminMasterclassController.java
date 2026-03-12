package com.modeltech.datamasteryhub.modules.communication.controller;
import com.modeltech.datamasteryhub.common.dto.ApiResponse;
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
 * Admin   : GET  /api/v1/admin/masterclass/{id}/registrations
 */
@RestController
@RequiredArgsConstructor
public class AdminMasterclassController {

    private final MasterclassService masterclassService;

    @GetMapping("/api/v1/admin/masterclass/{masterclassId}/registrations")
    public ResponseEntity<ApiResponse<java.util.List<MasterclassRegistrationResponseDTO>>> getRegistrations(
            @PathVariable String masterclassId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<MasterclassRegistrationResponseDTO> result =
                masterclassService.getAll(masterclassId, page, size);
        return ResponseEntity.ok(ApiResponse.page(
                result.getTotalElements() + " inscrit(s) pour cette masterclass",
                result));
    }

    @GetMapping("/api/v1/admin/masterclass/{masterclassId}/count")
    public ResponseEntity<ApiResponse<Long>> count(@PathVariable String masterclassId) {
        return ResponseEntity.ok(
                ApiResponse.ok("Nombre d'inscrits", masterclassService.count(masterclassId)));
    }
}
