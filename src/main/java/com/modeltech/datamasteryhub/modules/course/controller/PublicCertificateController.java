package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.course.dto.CertificatePayloads;
import com.modeltech.datamasteryhub.modules.course.service.CertificateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vérification publique d'un certificat (page ouverte depuis le QR code ou le lien LinkedIn). */
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@Tag(name = "Public - Certificats")
public class PublicCertificateController {

    private final CertificateService certificateService;
    private final IpRateLimiter rateLimiter;

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<CertificatePayloads.PublicCertificate>> verify(
            @PathVariable String publicId, HttpServletRequest request) {
        rateLimiter.check(request, IpRateLimiter.VERIFY_SCOPE);
        return ResponseEntity.ok(ApiResponse.ok("Certificat", certificateService.verify(publicId)));
    }

    @GetMapping("/{publicId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String publicId, HttpServletRequest request) {
        rateLimiter.check(request, IpRateLimiter.VERIFY_SCOPE);
        byte[] pdf = certificateService.pdf(publicId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("Certificat-" + publicId.toUpperCase() + ".pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }
}
