package com.modeltech.datamasteryhub.modules.communication.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "masterclass_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"masterclass_id", "email"}))
@Getter @Setter @NoArgsConstructor
public class MasterclassRegistration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identifiant de la masterclass (permet de réutiliser pour d'autres sessions) */
    @Column(name = "masterclass_id", nullable = false)
    private String masterclassId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    /** Étudiant, Professionnel, Entrepreneur, Autre */
    private String profile;

    private String company;

    /** Confirmation email envoyée */
    @Column(nullable = false)
    private Boolean emailSent = false;

    /** Notification Slack envoyée */
    @Column(nullable = false)
    private Boolean slackNotified = false;
}