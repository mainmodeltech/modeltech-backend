package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "project_feedback")
@Getter @Setter @NoArgsConstructor
public class ProjectFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private ProjectSubmission submission;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_role", nullable = false, length = 100)
    private String authorRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
}
