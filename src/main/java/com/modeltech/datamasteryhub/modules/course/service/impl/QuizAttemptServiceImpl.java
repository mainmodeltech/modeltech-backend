package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.LessonProgress;
import com.modeltech.datamasteryhub.modules.course.entity.QuizAttempt;
import com.modeltech.datamasteryhub.modules.course.entity.QuizChoice;
import com.modeltech.datamasteryhub.modules.course.entity.QuizQuestion;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonProgressRepository;
import com.modeltech.datamasteryhub.modules.course.repository.QuizAttemptRepository;
import com.modeltech.datamasteryhub.modules.course.repository.QuizChoiceRepository;
import com.modeltech.datamasteryhub.modules.course.repository.QuizQuestionRepository;
import com.modeltech.datamasteryhub.modules.course.service.LearnerAccess;
import com.modeltech.datamasteryhub.modules.course.service.QuizAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class QuizAttemptServiceImpl implements QuizAttemptService {

    private final SecureRandom random = new SecureRandom();

    private final CourseLessonRepository lessonRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizChoiceRepository choiceRepository;
    private final QuizAttemptRepository attemptRepository;
    private final LessonProgressRepository progressRepository;
    private final LearnerAccess access;
    private final com.modeltech.datamasteryhub.modules.course.service.CertificateService certificateService;

    // =========================================================================
    //  DÉMARRER
    // =========================================================================

    @Override
    @Transactional
    public EvaluationPayloads.QuizAttemptStart start(String learnerEmail, UUID quizId) {
        Learner learner = access.requireLearner(learnerEmail);
        CourseLesson lesson = lessonRepository.findByIdAndIsDeletedFalse(quizId)
                .filter(l -> l.getType() == LessonType.QUIZ && !l.getModule().isDeleted()
                        && l.getStatus() != LessonStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));
        access.requireAccess(learner, lesson.getModule().getBootcamp().getId());

        if (lesson.getStatus() == LessonStatus.SCHEDULED) throw access.forbidden("Ce quiz n'est pas encore disponible.");
        if (access.isModuleLocked(learner, lesson)) throw access.forbidden("Terminez d'abord les leçons du module précédent.");

        List<QuizAttempt> attempts = attemptRepository
                .findAllByLearnerIdAndLessonIdOrderByAttemptNumberAsc(learner.getId(), lesson.getId());
        if (attempts.stream().anyMatch(a -> Boolean.TRUE.equals(a.getPassed()))) {
            throw conflict("Vous avez déjà réussi ce quiz.");
        }

        // Une tentative ouverte (non rendue) est reprise telle quelle : on ne brûle pas de tentative en rechargeant la page
        Optional<QuizAttempt> open = attempts.stream().filter(a -> !a.isSubmitted()).findFirst();
        QuizAttempt attempt = open.orElseGet(() -> openNewAttempt(learner, lesson, attempts.size()));
        return toStart(attempt, lesson);
    }

    private QuizAttempt openNewAttempt(Learner learner, CourseLesson lesson, int previousAttempts) {
        Integer max = lesson.getQuizMaxAttempts();
        if (max != null && previousAttempts >= max) throw conflict("Vous avez utilisé toutes vos tentatives.");

        List<QuizQuestion> bank = new ArrayList<>(questionRepository
                .findAllByLessonIdAndIsDeletedFalseOrderByPositionAsc(lesson.getId()));
        if (bank.isEmpty()) throw conflict("Ce quiz n'est pas encore prêt.");
        Collections.shuffle(bank, random);
        int wanted = lesson.getQuizQuestionCount() != null ? lesson.getQuizQuestionCount() : bank.size();

        QuizAttempt attempt = new QuizAttempt();
        attempt.setLearner(learner);
        attempt.setLesson(lesson);
        attempt.setAttemptNumber(previousAttempts + 1);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setQuestionIds(bank.stream().limit(wanted).map(q -> q.getId().toString()).toList());
        return attemptRepository.save(attempt);
    }

    private EvaluationPayloads.QuizAttemptStart toStart(QuizAttempt attempt, CourseLesson lesson) {
        List<UUID> ids = attempt.getQuestionIds().stream().map(UUID::fromString).toList();
        Map<UUID, QuizQuestion> questions = questionRepository.findAllById(ids).stream()
                .filter(q -> !q.isDeleted()).collect(Collectors.toMap(QuizQuestion::getId, q -> q));
        Map<UUID, List<QuizChoice>> choices = choiceRepository.findAllByQuestionIds(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getQuestion().getId()));

        List<EvaluationPayloads.QuizQuestionView> views = new ArrayList<>();
        for (UUID id : ids) {
            QuizQuestion q = questions.get(id);
            if (q == null) continue;   // question retirée de la banque depuis le tirage
            views.add(EvaluationPayloads.QuizQuestionView.builder()
                    .id(q.getId().toString())
                    .text(q.getText())
                    // La bonne réponse ne quitte jamais le serveur avant la correction
                    .choices(choices.getOrDefault(id, List.of()).stream()
                            .map(c -> EvaluationPayloads.QuizChoiceView.builder()
                                    .id(c.getId().toString()).label(c.getLabel()).build())
                            .toList())
                    .build());
        }
        return EvaluationPayloads.QuizAttemptStart.builder()
                .attemptId(attempt.getId().toString())
                .quizId(lesson.getId().toString())
                .title(lesson.getTitle())
                .attemptNumber(attempt.getAttemptNumber())
                .maxAttempts(lesson.getQuizMaxAttempts())
                .passThreshold(lesson.getQuizPassThreshold())
                .timeLimitMinutes(null)
                .startedAt(attempt.getStartedAt())
                .questions(views)
                .build();
    }

    // =========================================================================
    //  RENDRE
    // =========================================================================

    @Override
    @Transactional
    public EvaluationPayloads.QuizAttemptResult submit(String learnerEmail, UUID attemptId,
                                                       EvaluationPayloads.QuizSubmission submission) {
        Learner learner = access.requireLearner(learnerEmail);
        QuizAttempt attempt = attemptRepository.findByIdAndLearnerId(attemptId, learner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tentative", "id", attemptId));
        if (attempt.isSubmitted()) throw conflict("Cette tentative a déjà été rendue.");

        CourseLesson lesson = attempt.getLesson();
        access.requireAccess(learner, lesson.getModule().getBootcamp().getId());

        List<UUID> ids = attempt.getQuestionIds().stream().map(UUID::fromString).toList();
        Map<UUID, QuizQuestion> questions = questionRepository.findAllById(ids).stream()
                .filter(q -> !q.isDeleted()).collect(Collectors.toMap(QuizQuestion::getId, q -> q));
        Map<UUID, List<QuizChoice>> choices = choiceRepository.findAllByQuestionIds(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getQuestion().getId()));
        List<UUID> graded = ids.stream().filter(questions::containsKey).toList();
        if (graded.isEmpty()) throw conflict("Ce quiz n'est plus disponible.");

        Map<String, String> given = submission.getAnswers() == null ? Map.of() : submission.getAnswers();
        Map<String, String> kept = new LinkedHashMap<>();
        List<EvaluationPayloads.QuizReviewItem> review = new ArrayList<>();
        int correctCount = 0;
        for (UUID id : graded) {
            List<QuizChoice> options = choices.getOrDefault(id, List.of());
            QuizChoice right = options.stream().filter(QuizChoice::isCorrect).findFirst().orElse(null);
            String chosen = given.get(id.toString());
            boolean validChoice = chosen != null && options.stream().anyMatch(c -> c.getId().toString().equals(chosen));
            if (validChoice) kept.put(id.toString(), chosen);
            if (validChoice && right != null && right.getId().toString().equals(chosen)) correctCount++;
            review.add(EvaluationPayloads.QuizReviewItem.builder()
                    .questionId(id.toString())
                    .chosenChoiceId(validChoice ? chosen : null)
                    .correctChoiceId(right != null ? right.getId().toString() : null)
                    .explanation(questions.get(id).getExplanation())
                    .build());
        }

        int score = (int) Math.round(correctCount * 100.0 / graded.size());
        boolean passed = score >= lesson.getQuizPassThreshold();
        attempt.setAnswers(kept);
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setCorrectCount(correctCount);
        attempt.setScore(score);
        attempt.setPassed(passed);
        attemptRepository.save(attempt);

        if (passed) {
            markLessonCompleted(learner, lesson);
            certificateService.issueIfEligible(learner.getId(), lesson.getModule().getBootcamp().getId());
        }

        Integer max = lesson.getQuizMaxAttempts();
        Integer remaining = max == null ? null : Math.max(0, max - attempt.getAttemptNumber());
        // La correction n'est donnée que lorsqu'elle ne permet plus de « jouer » la banque : réussite ou dernière tentative
        boolean revealAnswers = passed || (remaining != null && remaining == 0);

        log.info("Quiz {} rendu par {} : {}/{} ({} %) {}", lesson.getId(), learnerEmail,
                correctCount, graded.size(), score, passed ? "réussi" : "échoué");
        return EvaluationPayloads.QuizAttemptResult.builder()
                .attemptId(attempt.getId().toString())
                .quizId(lesson.getId().toString())
                .score(score)
                .passed(passed)
                .correctCount(correctCount)
                .questionCount(graded.size())
                .attemptNumber(attempt.getAttemptNumber())
                .attemptsRemaining(remaining)
                .review(revealAnswers ? review : null)
                .build();
    }

    private void markLessonCompleted(Learner learner, CourseLesson lesson) {
        LessonProgress progress = progressRepository.findByLearnerIdAndLessonId(learner.getId(), lesson.getId())
                .orElseGet(() -> {
                    LessonProgress created = new LessonProgress();
                    created.setLearner(learner);
                    created.setLesson(lesson);
                    return created;
                });
        progress.setCompleted(true);
        progressRepository.save(progress);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
