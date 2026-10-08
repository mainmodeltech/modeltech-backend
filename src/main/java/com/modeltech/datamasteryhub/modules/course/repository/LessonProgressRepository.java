package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.LessonProgress;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonProgressRepository extends SoftDeleteRepository<LessonProgress, UUID> {

    List<LessonProgress> findAllByLearnerIdAndIsDeletedFalse(UUID learnerId);

    Optional<LessonProgress> findByLearnerIdAndLessonId(UUID learnerId, UUID lessonId);

    List<LessonProgress> findAllByLearnerIdInAndIsDeletedFalse(java.util.Collection<UUID> learnerIds);
}
