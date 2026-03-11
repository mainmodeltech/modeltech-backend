package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.request.MasterclassRegistrationRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.MasterclassRegistrationResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.MasterclassService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public  : POST /api/v1/masterclass/register
 */

@RequestMapping("/api/v1/masterclass")
@RestController
@Slf4j
@RequiredArgsConstructor
public class PublicMasterclassController {

    private final MasterclassService masterclassService;

    // ── Rate Limiter — 5 requêtes / heure / IP ─────────────────────────────
    // ConcurrentHashMap : thread-safe, bucket créé à la première requête de chaque IP
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket getBucketForIp(String ip) {
        return buckets.computeIfAbsent(ip, key -> {
            Bandwidth limit = Bandwidth.classic(
                    5,                                       // 5 tentatives max
                    Refill.intervally(5, Duration.ofHours(1)) // rechargé toutes les heures
            );
            return Bucket.builder().addLimit(limit).build();
        });
    }

    private String extractIp(HttpServletRequest request) {
        // Support Traefik / reverse proxy (X-Forwarded-For)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // première IP = l'originale
        }
        return request.getRemoteAddr();
    }

    // ── Public ─────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<MasterclassRegistrationResponseDTO>> register(
            @Valid @RequestBody MasterclassRegistrationRequestDTO request,
            HttpServletRequest httpRequest) {

        log.info("Nouvelle tentative d'inscription à la masterclass : email={}, masterclassId={}, IP={}",
                request.getEmail(), request.getMasterclassId(), extractIp(httpRequest));

        // 1. Rate limiting par IP
        String clientIp = extractIp(httpRequest);
        Bucket bucket   = getBucketForIp(clientIp);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit dépassé pour IP : {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(
                            "Trop de tentatives. Veuillez réessayer dans une heure."));
        }

        // 2. Inscription (reCAPTCHA vérifié dans le service)
        try {
            MasterclassRegistrationResponseDTO data = masterclassService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(
                            "Inscription confirmée ! Vérifiez votre email pour le lien Google Meet.",
                            data));

        } catch (SecurityException e) {
            log.error("reCAPTCHA échoué pour email={}, masterclassId={}, IP={}: {}",
                    request.getEmail(), request.getMasterclassId(), clientIp, e.getMessage());
            // reCAPTCHA échoué
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (IllegalStateException e) {
            log.error("Échec d'inscription pour email={}, masterclassId={}, IP={}: {}",
                    request.getEmail(), request.getMasterclassId(), clientIp, e.getMessage());
            // Email déjà inscrit
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
