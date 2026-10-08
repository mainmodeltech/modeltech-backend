package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CourseLessonRepository extends SoftDeleteRepository<CourseLesson, UUID> {

    /** Leçons non supprimées des formations données, dans l'ordre du programme. */
    @Query("""
            SELECT l FROM CourseLesson l
            JOIN FETCH l.module m
            JOIN FETCH m.bootcamp b
            WHERE b.id IN :bootcampIds AND l.isDeleted = false AND m.isDeleted = false
            ORDER BY b.id, m.position, l.position
            """)
    List<CourseLesson> findAllByBootcampIds(@Param("bootcampIds") Collection<UUID> bootcampIds);
}
