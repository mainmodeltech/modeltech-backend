package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.QuizAttemptGrant;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuizAttemptGrantRepository extends SoftDeleteRepository<QuizAttemptGrant, UUID> {

    List<QuizAttemptGrant> findAllByLearnerIdAndIsDeletedFalse(UUID learnerId);

    List<QuizAttemptGrant> findAllByLearnerIdAndLessonIdAndIsDeletedFalse(UUID learnerId, UUID lessonId);

    List<QuizAttemptGrant> findAllByLearnerIdInAndIsDeletedFalse(Collection<UUID> learnerIds);
}
