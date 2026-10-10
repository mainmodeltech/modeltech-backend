package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.course.dto.LessonNotePayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.LessonNote;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Notes personnelles d'un apprenant : une par leçon, réservées à ses formations ouvertes. */
@Service
@RequiredArgsConstructor
public class LessonNoteService {

    private final LearnerAccess access;
    private final CourseLessonRepository lessonRepository;
    private final LessonNoteRepository noteRepository;

    @Transactional(readOnly = true)
    public LessonNotePayloads.Note get(String learnerEmail, UUID lessonId) {
        Learner learner = access.requireLearner(learnerEmail);
        requireLessonAccess(learner, lessonId);
        return noteRepository.findByLearnerIdAndLessonIdAndIsDeletedFalse(learner.getId(), lessonId)
                .map(LessonNoteService::toPayload)
                .orElseGet(() -> LessonNotePayloads.Note.builder().lessonId(lessonId.toString()).content("").build());
    }

    @Transactional
    public LessonNotePayloads.Note save(String learnerEmail, UUID lessonId, String content) {
        Learner learner = access.requireLearner(learnerEmail);
        CourseLesson lesson = requireLessonAccess(learner, lessonId);
        LessonNote note = noteRepository.findByLearnerIdAndLessonIdAndIsDeletedFalse(learner.getId(), lessonId)
                .orElseGet(() -> {
                    LessonNote created = new LessonNote();
                    created.setLearner(learner);
                    created.setLesson(lesson);
                    return created;
                });
        note.setContent(content == null ? "" : content);
        return toPayload(noteRepository.save(note));
    }

    private CourseLesson requireLessonAccess(Learner learner, UUID lessonId) {
        CourseLesson lesson = lessonRepository.findByIdAndIsDeletedFalse(lessonId)
                .filter(l -> !l.getModule().isDeleted() && l.getStatus() != LessonStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Leçon", "id", lessonId));
        access.requireAccess(learner, lesson.getModule().getBootcamp().getId());
        return lesson;
    }

    private static LessonNotePayloads.Note toPayload(LessonNote note) {
        return LessonNotePayloads.Note.builder()
                .lessonId(note.getLesson().getId().toString())
                .content(note.getContent())
                .updatedAt(note.getUpdatedAt() != null ? note.getUpdatedAt() : note.getCreatedAt())
                .build();
    }
}
