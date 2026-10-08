package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** L'appel d'un live pour une session a été fait (sans cette ligne, la présence est « inconnue »). */
@Entity
@Table(name = "live_roll_calls")
@Getter @Setter @NoArgsConstructor
public class LiveRollCall extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private BootcampSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private CourseLesson lesson;

    @Column(name = "taken_at", nullable = false)
    private LocalDateTime takenAt;

    @Column(name = "taken_by")
    private String takenBy;
}
