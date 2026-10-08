package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.LessonQuestion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonQuestionRepository extends SoftDeleteRepository<LessonQuestion, UUID> {

    List<LessonQuestion> findAllByLearnerIdAndLessonIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID learnerId, UUID lessonId);

    long countByLearnerIdAndLessonIdAndAnswerIsNullAndIsDeletedFalse(UUID learnerId, UUID lessonId);

    @Query("""
            SELECT q FROM LessonQuestion q JOIN FETCH q.learner JOIN FETCH q.lesson
            WHERE q.session.id = :sessionId AND q.isDeleted = false AND (:onlyOpen = false OR q.answer IS NULL)
            ORDER BY q.createdAt DESC
            """)
    List<LessonQuestion> findForSession(@Param("sessionId") UUID sessionId, @Param("onlyOpen") boolean onlyOpen);
}
