package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.common.util.TokenUtils;
import com.modeltech.datamasteryhub.modules.communication.dto.request.NewsletterSubscribeRequest;
import com.modeltech.datamasteryhub.modules.communication.dto.response.AdminNewsletterSubscriptionResponse;
import com.modeltech.datamasteryhub.modules.communication.entity.NewsletterSubscription;
import com.modeltech.datamasteryhub.modules.communication.enums.NewsletterStatus;
import com.modeltech.datamasteryhub.modules.communication.repository.NewsletterSubscriptionRepository;
import com.modeltech.datamasteryhub.modules.communication.service.NewsletterService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Abonnements newsletter avec double opt-in.
 *
 * <p>{@link #subscribe} n'est volontairement pas transactionnelle : une inscription
 * concurrente de la même adresse (violation d'unicité) doit pouvoir être absorbée
 * sans invalider une transaction englobante.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterServiceImpl implements NewsletterService {

    static final int CONFIRMATION_VALID_DAYS = 7;

    private final NewsletterSubscriptionRepository repository;
    private final NotificationService notificationService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void subscribe(NewsletterSubscribeRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        NewsletterSubscription subscription = repository.findByEmail(email).orElse(null);
        if (subscription != null && subscription.getStatus() == NewsletterStatus.CONFIRMED) {
            return; // déjà abonné : rien à envoyer, réponse publique identique
        }
        if (subscription == null) {
            subscription = new NewsletterSubscription();
            subscription.setEmail(email);
            subscription.setUnsubscribeToken(TokenUtils.randomToken());
        }
        if (request.getSource() != null && !request.getSource().isBlank()) {
            subscription.setSource(request.getSource().trim());
        }
        // Nouvelle demande (ou réabonnement) : un nouveau lien de confirmation, l'ancien devient invalide
        subscription.setStatus(NewsletterStatus.PENDING);
        subscription.setUnsubscribedAt(null);
        subscription.setConfirmationToken(TokenUtils.randomToken());
        subscription.setConfirmationExpiresAt(LocalDateTime.now().plusDays(CONFIRMATION_VALID_DAYS));

        try {
            repository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException concurrentSubscribe) {
            log.debug("Inscription newsletter concurrente ignorée pour {}", email);
            return;
        }

        String confirmLink = frontendUrl + "/newsletter/confirmation?token=" + subscription.getConfirmationToken();
        notificationService.sendNewsletterConfirmationEmail(email, confirmLink, CONFIRMATION_VALID_DAYS);
    }

    @Override
    public void confirm(String token) {
        NewsletterSubscription subscription = repository.findByConfirmationToken(token)
                .orElseThrow(NewsletterServiceImpl::invalidConfirmationLink);

        if (subscription.getStatus() == NewsletterStatus.CONFIRMED) {
            return; // double clic sur le lien
        }
        boolean expired = subscription.getConfirmationExpiresAt() == null
                || subscription.getConfirmationExpiresAt().isBefore(LocalDateTime.now());
        if (subscription.getStatus() != NewsletterStatus.PENDING || expired) {
            throw invalidConfirmationLink();
        }
        subscription.setStatus(NewsletterStatus.CONFIRMED);
        subscription.setConfirmedAt(LocalDateTime.now());
        repository.save(subscription);
    }

    @Override
    public void unsubscribe(String token) {
        NewsletterSubscription subscription = repository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Lien de désinscription invalide."));

        if (subscription.getStatus() == NewsletterStatus.UNSUBSCRIBED) {
            return;
        }
        subscription.setStatus(NewsletterStatus.UNSUBSCRIBED);
        subscription.setUnsubscribedAt(LocalDateTime.now());
        subscription.setConfirmationToken(null); // un ancien lien de confirmation ne réabonne pas
        subscription.setConfirmationExpiresAt(null);
        repository.save(subscription);
    }

    @Override
    public Page<AdminNewsletterSubscriptionResponse> findAllForAdmin(NewsletterStatus status, Pageable pageable) {
        Page<NewsletterSubscription> page = status == null
                ? repository.findAllByIsDeletedFalse(pageable)
                : repository.findAllByStatusAndIsDeletedFalse(status, pageable);
        return page.map(this::toAdminResponse);
    }

    private AdminNewsletterSubscriptionResponse toAdminResponse(NewsletterSubscription s) {
        return AdminNewsletterSubscriptionResponse.builder()
                .id(s.getId())
                .email(s.getEmail())
                .status(s.getStatus())
                .source(s.getSource())
                .confirmedAt(s.getConfirmedAt())
                .unsubscribedAt(s.getUnsubscribedAt())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private static ResponseStatusException invalidConfirmationLink() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lien de confirmation invalide ou expiré.");
    }
}
