package com.modeltech.datamasteryhub.modules.communication.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.communication.enums.NewsletterStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Abonnement à la newsletter (double opt-in) : PENDING jusqu'au clic sur le lien
 * de confirmation, puis CONFIRMED ; UNSUBSCRIBED via le lien de désinscription.
 * Les jetons ne sont jamais exposés par l'API admin.
 */
@Entity
@Table(name = "newsletter_subscriptions")
@Getter @Setter @NoArgsConstructor
public class NewsletterSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Adresse normalisée en minuscules. */
    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsletterStatus status = NewsletterStatus.PENDING;

    /** Où l'abonnement a été demandé (ex. « ressources »). */
    @Column(length = 50)
    private String source;

    @Column(name = "confirmation_token", unique = true, length = 64)
    private String confirmationToken;

    @Column(name = "confirmation_expires_at")
    private LocalDateTime confirmationExpiresAt;

    @Column(name = "unsubscribe_token", nullable = false, unique = true, length = 64)
    private String unsubscribeToken;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;
}
