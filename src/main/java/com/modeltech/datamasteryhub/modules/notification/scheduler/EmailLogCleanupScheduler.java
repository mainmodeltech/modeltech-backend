package com.modeltech.datamasteryhub.modules.notification.scheduler;

import com.modeltech.datamasteryhub.modules.notification.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Le journal des e-mails sert au diagnostic : on garde {@code app.mail.log-retention-days} jours (90 par défaut). */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailLogCleanupScheduler {

    private final EmailLogRepository repository;

    @Value("${app.mail.log-retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        int deleted = repository.deleteOlderThan(LocalDateTime.now().minusDays(retentionDays));
        if (deleted > 0) {
            log.info("Journal des e-mails : {} entrée(s) de plus de {} jours supprimée(s)", deleted, retentionDays);
        }
    }
}
