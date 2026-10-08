package com.modeltech.datamasteryhub.modules.notification.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Trace d'un e-mail sortant : qui, quoi (objet), résultat. Jamais le corps du message. */
@Entity
@Table(name = "email_logs")
@Getter @Setter @NoArgsConstructor
public class EmailLog extends BaseEntity {

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nature du message (ex. PAYMENT_LINK, PASSWORD_RESET). */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false)
    private String recipient;

    @Column(length = 500)
    private String subject;

    /** {@link #SENT}, {@link #FAILED} ou {@link #SKIPPED} (messagerie non configurée). */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(nullable = false)
    private Integer attempts = 1;

    @Column(length = 1000)
    private String error;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
