package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.QuizChoice;
import com.modeltech.datamasteryhub.modules.course.entity.QuizQuestion;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.QuizChoiceRepository;
import com.modeltech.datamasteryhub.modules.course.repository.QuizQuestionRepository;
import com.modeltech.datamasteryhub.modules.course.service.CourseAccessPolicy;
import com.modeltech.datamasteryhub.modules.course.service.QuizAuthoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class QuizAuthoringServiceImpl implements QuizAuthoringService {

    private static final int MAX_QUESTIONS = 200;
    private static final int MIN_CHOICES = 2;
    private static final int MAX_CHOICES = 8;

    private final CourseLessonRepository lessonRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizChoiceRepository choiceRepository;
    private final CourseAccessPolicy accessPolicy;

    @Override
    public EvaluationPayloads.QuizBank get(UUID lessonId, String actorEmail, Collection<String> actorRoles) {
        CourseLesson lesson = requireQuizLesson(lessonId, actorEmail, actorRoles);
        return toBank(lesson);
    }

    @Override
    @Transactional
    public EvaluationPayloads.QuizBank save(UUID lessonId, EvaluationPayloads.QuizBank bank,
                                            String actorEmail, Collection<String> actorRoles) {
        CourseLesson lesson = requireQuizLesson(lessonId, actorEmail, actorRoles);
        List<EvaluationPayloads.QuizQuestionEdit> edits = bank.getQuestions() == null ? List.of() : bank.getQuestions();
        validate(edits);

        Map<UUID, QuizQuestion> existingQuestions = questionRepository
                .findAllByLessonIdAndIsDeletedFalseOrderByPositionAsc(lessonId).stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        Map<UUID, QuizChoice> existingChoices = existingQuestions.isEmpty() ? Map.of()
                : choiceRepository.findAllByQuestionIds(existingQuestions.keySet()).stream()
                        .collect(Collectors.toMap(QuizChoice::getId, Function.identity()));

        Set<UUID> keptQuestions = new HashSet<>();
        Set<UUID> keptChoices = new HashSet<>();
        int questionPosition = 0;
        for (EvaluationPayloads.QuizQuestionEdit qe : edits) {
            QuizQuestion question = match(qe.getId(), existingQuestions, keptQuestions);
            if (question == null) {
                question = new QuizQuestion();
                question.setLesson(lesson);
            }
            question.setPosition(++questionPosition);
            question.setText(qe.getText().trim());
            question.setExplanation(blankToNull(qe.getExplanation()));
            question = questionRepository.save(question);
            keptQuestions.add(question.getId());

            int choicePosition = 0;
            for (EvaluationPayloads.QuizChoiceEdit ce : qe.getChoices()) {
                QuizChoice choice = match(ce.getId(), existingChoices, keptChoices);
                if (choice == null) choice = new QuizChoice();
                choice.setQuestion(question);
                choice.setPosition(++choicePosition);
                choice.setLabel(ce.getLabel().trim());
                choice.setCorrect(Boolean.TRUE.equals(ce.getCorrect()));
                keptChoices.add(choiceRepository.save(choice).getId());
            }
        }
        softDeleteMissing(existingChoices, keptChoices, choiceRepository::save);
        softDeleteMissing(existingQuestions, keptQuestions, questionRepository::save);
        log.info("Banque de questions de la leçon {} enregistrée par {} : {} question(s)",
                lessonId, actorEmail, keptQuestions.size());
        return toBank(lesson);
    }

    // ── Privé ───────────────────────────────────────────────────────

    private CourseLesson requireQuizLesson(UUID lessonId, String actorEmail, Collection<String> actorRoles) {
        CourseLesson lesson = lessonRepository.findByIdAndIsDeletedFalse(lessonId)
                .filter(l -> !l.getModule().isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Leçon", "id", lessonId));
        if (lesson.getType() != LessonType.QUIZ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette leçon n'est pas un quiz.");
        }
        accessPolicy.requireEditable(lesson.getModule().getBootcamp(), actorEmail, actorRoles);
        return lesson;
    }

    private EvaluationPayloads.QuizBank toBank(CourseLesson lesson) {
        List<QuizQuestion> questions = questionRepository
                .findAllByLessonIdAndIsDeletedFalseOrderByPositionAsc(lesson.getId());
        Map<UUID, List<QuizChoice>> choices = questions.isEmpty() ? Map.of()
                : choiceRepository.findAllByQuestionIds(questions.stream().map(QuizQuestion::getId).toList()).stream()
                        .collect(Collectors.groupingBy(c -> c.getQuestion().getId()));
        return EvaluationPayloads.QuizBank.builder()
                .lessonId(lesson.getId().toString())
                .title(lesson.getTitle())
                .questionCount(lesson.getQuizQuestionCount())
                .passThreshold(lesson.getQuizPassThreshold())
                .maxAttempts(lesson.getQuizMaxAttempts())
                .questions(questions.stream().map(q -> EvaluationPayloads.QuizQuestionEdit.builder()
                        .id(q.getId().toString())
                        .text(q.getText())
                        .explanation(q.getExplanation())
                        .choices(choices.getOrDefault(q.getId(), List.of()).stream()
                                .map(c -> EvaluationPayloads.QuizChoiceEdit.builder()
                                        .id(c.getId().toString()).label(c.getLabel()).correct(c.isCorrect()).build())
                                .toList())
                        .build()).toList())
                .build();
    }

    private void validate(List<EvaluationPayloads.QuizQuestionEdit> questions) {
        if (questions.size() > MAX_QUESTIONS) throw bad("200 questions maximum par quiz.");
        Set<String> ids = new HashSet<>();
        int number = 0;
        for (EvaluationPayloads.QuizQuestionEdit q : questions) {
            number++;
            if (q.getText() == null || q.getText().isBlank()) throw bad("La question " + number + " n'a pas d'énoncé.");
            if (q.getText().length() > 2000) throw bad("L'énoncé de la question " + number + " dépasse 2000 caractères.");
            if (q.getExplanation() != null && q.getExplanation().length() > 2000) {
                throw bad("L'explication de la question " + number + " dépasse 2000 caractères.");
            }
            requireUnique(q.getId(), ids);
            List<EvaluationPayloads.QuizChoiceEdit> choices = q.getChoices() == null ? List.of() : q.getChoices();
            if (choices.size() < MIN_CHOICES || choices.size() > MAX_CHOICES) {
                throw bad("La question " + number + " doit avoir entre 2 et 8 réponses possibles.");
            }
            long correct = choices.stream().filter(c -> Boolean.TRUE.equals(c.getCorrect())).count();
            if (correct != 1) throw bad("La question " + number + " doit avoir exactement une bonne réponse.");
            for (EvaluationPayloads.QuizChoiceEdit c : choices) {
                if (c.getLabel() == null || c.getLabel().isBlank()) throw bad("La question " + number + " a une réponse vide.");
                if (c.getLabel().length() > 500) throw bad("Une réponse de la question " + number + " dépasse 500 caractères.");
                requireUnique(c.getId(), ids);
            }
        }
    }

    private void requireUnique(String id, Set<String> seen) {
        if (id != null && !id.isBlank() && !seen.add(id)) throw bad("Identifiant en double : " + id);
    }

    private <T> T match(String id, Map<UUID, T> existing, Set<UUID> used) {
        if (id == null || id.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(id.trim());
            return used.contains(uuid) ? null : existing.get(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private <T extends com.modeltech.datamasteryhub.common.persistence.BaseEntity> void softDeleteMissing(
            Map<UUID, T> existing, Set<UUID> kept, java.util.function.Consumer<T> save) {
        existing.forEach((id, entity) -> {
            if (kept.contains(id)) return;
            entity.setDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy("system");
            save.accept(entity);
        });
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
