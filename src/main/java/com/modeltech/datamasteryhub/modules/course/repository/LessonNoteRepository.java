package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.LessonNote;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonNoteRepository extends SoftDeleteRepository<LessonNote, UUID> {

    Optional<LessonNote> findByLearnerIdAndLessonIdAndIsDeletedFalse(UUID learnerId, UUID lessonId);
}
