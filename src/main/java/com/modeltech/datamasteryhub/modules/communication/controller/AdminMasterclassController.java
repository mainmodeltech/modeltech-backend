package com.modeltech.datamasteryhub.modules.communication.controller;
import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.response.MasterclassRegistrationResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.MasterclassPayloads;
import com.modeltech.datamasteryhub.modules.communication.service.MasterclassEventService;
import com.modeltech.datamasteryhub.modules.communication.service.MasterclassService;
import org.springframework.security.core.Authentication;
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
    private final MasterclassEventService events;

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

    // ── Événements (une masterclass = un événement daté) ────────────────────

    @GetMapping("/api/v1/admin/masterclasses")
    public ResponseEntity<ApiResponse<java.util.List<MasterclassPayloads.AdminEvent>>> events() {
        return ResponseEntity.ok(ApiResponse.ok("Masterclass", events.list()));
    }

    @GetMapping("/api/v1/admin/masterclasses/{id}")
    public ResponseEntity<ApiResponse<MasterclassPayloads.AdminEvent>> event(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Masterclass", events.get(id)));
    }

    @PostMapping("/api/v1/admin/masterclasses")
    public ResponseEntity<ApiResponse<MasterclassPayloads.AdminEvent>> create(@Valid @RequestBody MasterclassPayloads.EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Masterclass créée", events.create(request)));
    }

    @PutMapping("/api/v1/admin/masterclasses/{id}")
    public ResponseEntity<ApiResponse<MasterclassPayloads.AdminEvent>> update(
            @PathVariable java.util.UUID id, @Valid @RequestBody MasterclassPayloads.EventRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Masterclass mise à jour", events.update(id, request)));
    }

    @DeleteMapping("/api/v1/admin/masterclasses/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable java.util.UUID id) {
        events.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Masterclass supprimée", null));
    }

    /** Inscrits d'un événement (les plus récents d'abord). */
    @GetMapping("/api/v1/admin/masterclasses/{id}/registrations")
    public ResponseEntity<ApiResponse<java.util.List<MasterclassRegistrationResponseDTO>>> eventRegistrations(
            @PathVariable java.util.UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        String code = events.get(id).getCode();
        Page<MasterclassRegistrationResponseDTO> result = masterclassService.getAll(code, page, size);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " inscrit(s)", result));
    }

    @PatchMapping("/api/v1/admin/masterclasses/{id}/registrations/{registrationId}/attendance")
    public ResponseEntity<ApiResponse<MasterclassRegistrationResponseDTO>> attendance(
            @PathVariable java.util.UUID id, @PathVariable java.util.UUID registrationId,
            @Valid @RequestBody MasterclassPayloads.AttendanceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Présence enregistrée",
                masterclassService.setAttendance(events.get(id).getCode(), registrationId, request.getAttendance())));
    }

    /** Écrit aux inscrits (tous, présents ou absents). */
    @PostMapping("/api/v1/admin/masterclasses/{id}/message")
    public ResponseEntity<ApiResponse<MasterclassPayloads.MessageResult>> message(
            @PathVariable java.util.UUID id, @Valid @RequestBody MasterclassPayloads.MessageRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Message envoyé", events.message(id, request, authentication.getName())));
    }
}
