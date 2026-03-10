package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Inscription d'un visiteur a un bootcamp.
 */
@Entity
@Table(name = "registrations")
@Getter @Setter @NoArgsConstructor
public class Registration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bootcamp_id")
    private Bootcamp bootcamp;

    @Column(name = "bootcamp_title")
    private String bootcampTitle;

    // Session liee
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bootcamp_session_id")
    private BootcampSession session;

    @Column(name = "session_name")
    private String sessionName;

    // Code promo utilise
    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    @Column(name = "promo_code_used", length = 50)
    private String promoCodeUsed;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;
    private String company;
    private String position;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RegistrationStatus status = RegistrationStatus.PENDING;
}
