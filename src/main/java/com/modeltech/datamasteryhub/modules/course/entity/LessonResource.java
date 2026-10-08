package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "lesson_resources")
@Getter @Setter @NoArgsConstructor
public class LessonResource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private CourseLesson lesson;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private String name;

    /** Extension affichée dans la pastille : PDF, XLSX, PBIX… */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "size_label", length = 50)
    private String sizeLabel;

    @Column(length = 500)
    private String note;

    @Column(length = 2048)
    private String url;

    /** Débloquée seulement après réussite du quiz du module. */
    @Column(name = "locked_until_quiz", nullable = false)
    private boolean lockedUntilQuiz;
}
