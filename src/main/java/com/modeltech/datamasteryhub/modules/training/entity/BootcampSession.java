package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.enums.SessionFormat;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bootcamp_sessions")
@Getter @Setter @NoArgsConstructor
public class BootcampSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bootcamp_id", nullable = false)
    private Bootcamp bootcamp;

    // Identité de la session
    @Column(name = "session_name")
    private String sessionName;          // ex: "Cohorte 5 - Janvier 2025"

    @Column(name = "cohort_number")
    private Integer cohortNumber;        // numéro de cohorte

    private Integer year;

    // Dates
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "registration_deadline")
    private LocalDate registrationDeadline;

    // Capacité
    @Column(name = "max_participants")
    private Integer maxParticipants = 20;

    @Column(name = "current_participants")
    private Integer currentParticipants = 0;

    @Column(name = "is_full")
    private Boolean isFull = false;

    // État
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private SessionStatus status = SessionStatus.UPCOMING;

    // Format
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private SessionFormat format = SessionFormat.PRESENTIEL;

    private String location;

    // Prix
    @Column(name = "price_override")
    private String priceOverride;        // null = utilise le prix du bootcamp

    @Column(name = "early_bird_price")
    private String earlyBirdPrice;

    @Column(name = "early_bird_deadline")
    private LocalDate earlyBirdDeadline;

    // Visibilité
    @Column(name = "is_featured")
    private Boolean isFeatured = false;

    private Boolean published = true;
}
