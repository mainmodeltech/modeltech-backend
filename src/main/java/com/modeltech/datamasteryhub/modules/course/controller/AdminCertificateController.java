package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.course.dto.CertificatePayloads;
import com.modeltech.datamasteryhub.modules.course.service.CertificateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Certificats côté back-office : liste, délivrance depuis le suivi de session, révocation, renvoi. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Certificats")
public class AdminCertificateController {

    private final CertificateService certificateService;

    @GetMapping("/certificates")
    public ResponseEntity<ApiResponse<List<CertificatePayloads.AdminCertificate>>> getAll(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CertificatePayloads.AdminCertificate> page = certificateService.findAllForAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " certificat(s)", page));
    }

    /** Bouton « Délivrer le certificat » de la page de suivi de session. */
    @PostMapping("/sessions/{sessionId}/learners/{learnerId}/certificate")
    public ResponseEntity<ApiResponse<CertificatePayloads.AdminCertificate>> issue(
            @PathVariable UUID sessionId, @PathVariable UUID learnerId,
            @RequestBody(required = false) CertificatePayloads.IssueRequest request,
            Authentication authentication) {
        CertificatePayloads.IssueRequest body = request != null ? request : new CertificatePayloads.IssueRequest();
        List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Certificat délivré",
                certificateService.issueManually(sessionId, learnerId, body, authentication.getName(), roles)));
    }

    @PostMapping("/certificates/{publicId}/revoke")
    public ResponseEntity<ApiResponse<CertificatePayloads.AdminCertificate>> revoke(
            @PathVariable String publicId, @Valid @RequestBody CertificatePayloads.RevokeRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Certificat révoqué",
                certificateService.revoke(publicId, request.getReason(), authentication.getName())));
    }

    @PostMapping("/certificates/{publicId}/resend")
    public ResponseEntity<ApiResponse<Void>> resend(@PathVariable String publicId) {
        certificateService.resend(publicId);
        return ResponseEntity.ok(ApiResponse.ok("E-mail renvoyé", null));
    }
}
