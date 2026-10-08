package com.modeltech.datamasteryhub.modules.notification.service.impl;

import com.modeltech.datamasteryhub.modules.notification.dto.EmailLogResponse;
import com.modeltech.datamasteryhub.modules.notification.dto.TestEmailResponse;
import com.modeltech.datamasteryhub.modules.notification.entity.EmailLog;
import com.modeltech.datamasteryhub.modules.notification.repository.EmailLogRepository;
import com.modeltech.datamasteryhub.modules.notification.service.EmailLogService;
import com.modeltech.datamasteryhub.modules.notification.service.ResilientMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailLogServiceImpl implements EmailLogService {

    private final EmailLogRepository repository;
    private final ResilientMailSender mailSender;

    @Override
    public Page<EmailLogResponse> findAll(String status, String type, Pageable pageable) {
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        return repository.search(normalizedStatus, normalizedType, pageable).map(this::toResponse);
    }

    @Override
    public ResilientMailSender.MailStatus status() {
        return mailSender.status();
    }

    @Override
    public TestEmailResponse sendTest(String to) {
        if (!mailSender.isConfigured()) {
            return TestEmailResponse.builder().sent(false)
                    .message("Messagerie non configurée : renseignez MAIL_USERNAME et MAIL_PASSWORD.").build();
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailSender.fromAddress());
        message.setTo(to.trim());
        message.setSubject("[Model Technologie] E-mail de test");
        message.setText("Ce message confirme que la messagerie de la plateforme fonctionne.\n\n— Model Technologie");
        boolean sent = mailSender.send(message, "TEST");
        String effective = message.getTo() != null && message.getTo().length > 0 ? message.getTo()[0] : to;
        return TestEmailResponse.builder()
                .sent(sent)
                .deliveredTo(effective)
                .message(sent ? "E-mail envoyé." : "Échec de l'envoi : " + mailSender.status().lastError())
                .build();
    }

    private EmailLogResponse toResponse(EmailLog l) {
        return EmailLogResponse.builder()
                .id(l.getId()).type(l.getType()).recipient(l.getRecipient()).subject(l.getSubject())
                .status(l.getStatus()).attempts(l.getAttempts()).error(l.getError())
                .sentAt(l.getSentAt()).createdAt(l.getCreatedAt())
                .build();
    }
}
