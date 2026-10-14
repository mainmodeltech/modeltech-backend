package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.LessonQuestion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /**
     * Boîte de réception de l'équipe : questions de toutes les sessions visibles. {@code allSessions} faux : seulement celles dont
     * le formateur est {@code trainerEmail}. {@code status} : OPEN (sans réponse), ANSWERED ou ALL. {@code like} : motif déjà
     * préparé en minuscules (« %mot% »), « % » pour tout.
     */
    @Query(value = """
            SELECT q FROM LessonQuestion q JOIN FETCH q.learner l JOIN FETCH q.lesson JOIN FETCH q.session s JOIN FETCH s.bootcamp
            LEFT JOIN s.trainer t
            WHERE q.isDeleted = false
              AND (:status = 'ALL' OR (:status = 'OPEN' AND q.answer IS NULL) OR (:status = 'ANSWERED' AND q.answer IS NOT NULL))
              AND (:allSessions = true OR lower(t.email) = :trainerEmail)
              AND (:sessionId IS NULL OR s.id = :sessionId)
              AND (lower(q.question) LIKE :like OR lower(l.firstName) LIKE :like OR lower(coalesce(l.lastName, '')) LIKE :like
                   OR lower(q.lesson.title) LIKE :like OR lower(coalesce(s.sessionName, '')) LIKE :like)
            """,
            countQuery = """
            SELECT count(q) FROM LessonQuestion q JOIN q.learner l JOIN q.lesson JOIN q.session s LEFT JOIN s.trainer t
            WHERE q.isDeleted = false
              AND (:status = 'ALL' OR (:status = 'OPEN' AND q.answer IS NULL) OR (:status = 'ANSWERED' AND q.answer IS NOT NULL))
              AND (:allSessions = true OR lower(t.email) = :trainerEmail)
              AND (:sessionId IS NULL OR s.id = :sessionId)
              AND (lower(q.question) LIKE :like OR lower(l.firstName) LIKE :like OR lower(coalesce(l.lastName, '')) LIKE :like
                   OR lower(q.lesson.title) LIKE :like OR lower(coalesce(s.sessionName, '')) LIKE :like)
            """)
    Page<LessonQuestion> inbox(@Param("status") String status, @Param("allSessions") boolean allSessions,
                               @Param("trainerEmail") String trainerEmail, @Param("sessionId") UUID sessionId,
                               @Param("like") String like, Pageable pageable);
}
