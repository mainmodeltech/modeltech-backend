package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.notification.dto.EmailLogResponse;
import com.modeltech.datamasteryhub.modules.notification.dto.TestEmailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Diagnostic de la messagerie depuis le back-office. */
public interface EmailLogService {

    Page<EmailLogResponse> findAll(String status, String type, Pageable pageable);

    ResilientMailSender.MailStatus status();

    /** Envoie un message de contrôle (synchrone) et dit s'il est parti. */
    TestEmailResponse sendTest(String to);
}
