package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Un rappel de live déjà envoyé (live, session, moment) : garantit qu'il ne part qu'une fois. */
@Entity
@Table(name = "live_reminders")
@Getter @Setter @NoArgsConstructor
public class LiveReminder extends BaseEntity {

    public static final String H24 = "H24";
    public static final String H1 = "H1";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private CourseLesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private BootcampSession session;

    @Column(nullable = false, length = 10, updatable = false)
    private String kind;

    @Column(nullable = false)
    private Integer recipients = 0;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
