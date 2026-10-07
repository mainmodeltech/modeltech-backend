package com.modeltech.datamasteryhub.modules.training.scheduler;

import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Relance automatique des paiements en attente (J+2 après le lien, puis tous les 2 jours, 3 relances max). */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderScheduler {

    private final PaymentService paymentService;

    @Scheduled(cron = "${app.payment.reminder-cron:0 0 9 * * *}")
    public void sendReminders() {
        try {
            paymentService.sendDueReminders(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Échec de l'envoi des relances de paiement : {}", e.getMessage(), e);
        }
    }
}
