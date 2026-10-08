package com.modeltech.datamasteryhub.modules.course.scheduler;

import com.modeltech.datamasteryhub.modules.course.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Filet de sécurité de la délivrance automatique : rattrape les cas que les événements ne voient pas
 * (règles du certificat modifiées après coup, leçon retirée du programme…).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateSweepScheduler {

    private final CertificateService certificateService;

    @Scheduled(cron = "${app.certificate.sweep-cron:0 0 2 * * *}")
    public void sweep() {
        try {
            certificateService.sweep();
        } catch (Exception e) {
            log.error("Échec du balayage des certificats : {}", e.getMessage(), e);
        }
    }
}
