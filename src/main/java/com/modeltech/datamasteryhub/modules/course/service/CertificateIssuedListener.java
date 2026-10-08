package com.modeltech.datamasteryhub.modules.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * L'e-mail « certificat prêt » ne part qu'une fois la délivrance validée en base :
 * une transaction annulée ne doit jamais féliciter quelqu'un pour un certificat inexistant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateIssuedListener {

    private final CertificateService certificateService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIssued(CertificateIssuedEvent event) {
        try {
            certificateService.notifyIssued(event.publicId());
        } catch (RuntimeException e) {
            // Le certificat existe : un échec d'e-mail se rattrape par « renvoyer » dans le back-office
            log.error("E-mail du certificat {} non envoyé : {}", event.publicId(), e.getMessage());
        }
    }
}
