package com.modeltech.datamasteryhub.modules.course.scheduler;

import com.modeltech.datamasteryhub.modules.course.service.LearnerAccess;
import com.modeltech.datamasteryhub.modules.course.service.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Rappels de live : toutes les 15 minutes, 24 h puis 1 h avant (une seule fois par live et par session). */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiveReminderScheduler {

    private final MessagingService messagingService;
    private final LearnerAccess learnerAccess;

    @Scheduled(cron = "${app.messaging.live-reminder-cron:0 */15 * * * *}")
    public void remind() {
        try {
            messagingService.sendDueLiveReminders(LocalDateTime.now(learnerAccess.zone()));
        } catch (Exception e) {
            log.error("Échec des rappels de live : {}", e.getMessage(), e);
        }
    }
}
