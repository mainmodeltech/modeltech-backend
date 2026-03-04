package com.modeltech.datamasteryhub.modules.notification.service.impl;

import com.modeltech.datamasteryhub.modules.notification.service.EmailNotifier;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.notification.service.SlackNotifier;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final SlackNotifier slackNotifier;
    private final EmailNotifier emailNotifier;

    @Override
    @Async
    public void notifyNewRegistration(Registration registration) {
        log.info("Envoi des notifications pour l'inscription de {} {}",
                registration.getFirstName(), registration.getLastName());

        try {
            slackNotifier.send(registration);
        } catch (Exception e) {
            log.error("Erreur Slack notification : {}", e.getMessage());
        }

        try {
            emailNotifier.send(registration);
        } catch (Exception e) {
            log.error("Erreur Email notification : {}", e.getMessage());
        }
    }
}
