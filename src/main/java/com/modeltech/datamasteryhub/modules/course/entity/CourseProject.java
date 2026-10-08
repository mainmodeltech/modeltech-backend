package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/** Consigne du projet final d'une formation. */
@Entity
@Table(name = "course_projects")
@Getter @Setter @NoArgsConstructor
public class CourseProject extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bootcamp_id", nullable = false, updatable = false)
    private Bootcamp bootcamp;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String brief;

    /** Texte libre affiché tel quel (ex. « Avant le 30 novembre »). */
    @Column(name = "deadline_label", length = 100)
    private String deadlineLabel;

    /** Extensions acceptées en minuscules, sans point. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "accepted_extensions", nullable = false, columnDefinition = "text[]")
    private List<String> acceptedExtensions;

    @Column(name = "max_size_mb", nullable = false)
    private Integer maxSizeMb;
}
