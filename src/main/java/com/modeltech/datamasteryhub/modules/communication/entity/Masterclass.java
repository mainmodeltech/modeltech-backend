package com.modeltech.datamasteryhub.modules.communication.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une masterclass (événement gratuit, en ligne ou sur place) : une par mois ou tous les deux mois. Les inscriptions s'y
 * rattachent par {@link #code}. Le lien de connexion n'est jamais exposé publiquement : il est envoyé aux inscrits.
 */
@Entity
@Table(name = "masterclasses")
@Getter @Setter @NoArgsConstructor
public class Masterclass extends BaseEntity {

    public static final String ONLINE = "ONLINE";
    public static final String ONSITE = "ONSITE";
    public static final String HYBRID = "HYBRID";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 120;

    @Column(nullable = false, length = 10)
    private String format = ONLINE;

    @Column(length = 255)
    private String place;

    @Column(name = "join_url", length = 500)
    private String joinUrl;

    /** Nombre de places ; null = illimité. */
    private Integer capacity;

    @Column(nullable = false)
    private boolean published;

    /** Inscriptions fermées à la main (complet avant l'heure, événement annulé…). */
    @Column(name = "registrations_closed", nullable = false)
    private boolean registrationsClosed;

    public LocalDateTime endsAt() {
        return startsAt.plusMinutes(durationMinutes);
    }

    public boolean isOver(LocalDateTime now) {
        return endsAt().isBefore(now);
    }
}
