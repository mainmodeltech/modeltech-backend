package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.LessonResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface LessonResourceRepository extends SoftDeleteRepository<LessonResource, UUID> {

    @Query("""
            SELECT r FROM LessonResource r
            WHERE r.lesson.id IN :lessonIds AND r.isDeleted = false
            ORDER BY r.position
            """)
    List<LessonResource> findAllByLessonIds(@Param("lessonIds") Collection<UUID> lessonIds);
}
