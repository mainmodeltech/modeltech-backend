package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "live_attendance")
@Getter @Setter @NoArgsConstructor
public class LiveAttendance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roll_call_id", nullable = false, updatable = false)
    private LiveRollCall rollCall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false, updatable = false)
    private Learner learner;

    @Column(nullable = false)
    private boolean present;
}
