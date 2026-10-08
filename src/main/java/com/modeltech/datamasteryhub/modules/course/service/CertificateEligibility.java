package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseConfig;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.LessonProgress;
import com.modeltech.datamasteryhub.modules.course.entity.LiveRollCall;
import com.modeltech.datamasteryhub.modules.course.entity.QuizAttempt;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.enums.ProjectStatus;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.CourseProjectRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonProgressRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LiveAttendanceRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LiveRollCallRepository;
import com.modeltech.datamasteryhub.modules.course.repository.ProjectSubmissionRepository;
import com.modeltech.datamasteryhub.modules.course.repository.QuizAttemptRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Calcule, pour un apprenant, où il en est des conditions d'obtention du certificat. */
@Component
@RequiredArgsConstructor
public class CertificateEligibility {

    private final CourseContentAssembler assembler;
    private final CourseLessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final QuizAttemptRepository attemptRepository;
    private final CourseProjectRepository projectRepository;
    private final ProjectSubmissionRepository submissionRepository;
    private final LiveRollCallRepository rollCallRepository;
    private final LiveAttendanceRepository attendanceRepository;

    /**
     * @param includesProject le projet final fait partie des conditions (exigé par la formation et défini)
     * @param eligible        toutes les conditions sont remplies
     */
    public record Evaluation(List<EvaluationPayloads.CertificateCondition> conditions, boolean eligible, boolean includesProject) {}

    public Evaluation evaluate(Learner learner, Bootcamp bootcamp, BootcampSession session) {
        CourseConfig config = assembler.configOf(bootcamp);
        List<CourseLesson> lessons = lessonRepository.findAllByBootcampIds(List.of(bootcamp.getId())).stream()
                .filter(l -> l.getStatus() != LessonStatus.DRAFT).toList();
        Set<UUID> lessonIds = lessons.stream().map(CourseLesson::getId).collect(Collectors.toSet());

        Set<UUID> completed = progressRepository.findAllByLearnerIdAndIsDeletedFalse(learner.getId()).stream()
                .filter(LessonProgress::isCompleted).map(p -> p.getLesson().getId())
                .filter(lessonIds::contains).collect(Collectors.toSet());
        Set<UUID> passedQuizzes = attemptRepository.findAllByLearnerIdAndSubmittedAtNotNull(learner.getId()).stream()
                .filter(a -> Boolean.TRUE.equals(a.getPassed()) && lessonIds.contains(a.getLesson().getId()))
                .map(a -> a.getLesson().getId()).collect(Collectors.toSet());

        boolean hasProject = projectRepository.findByBootcampId(bootcamp.getId()).filter(p -> !p.isDeleted()).isPresent();
        boolean includesProject = hasProject && config.isFinalProjectValidated();
        ProjectStatus project = includesProject
                ? submissionRepository.findByLearnerIdAndBootcampId(learner.getId(), bootcamp.getId())
                        .map(s -> s.getStatus()).orElse(ProjectStatus.NOT_STARTED)
                : null;

        int livesTotal = 0;
        int livesPresent = 0;
        if (session != null) {
            Set<UUID> liveLessonIds = lessons.stream().filter(l -> l.getType() == LessonType.LIVE)
                    .map(CourseLesson::getId).collect(Collectors.toSet());
            Map<UUID, LiveRollCall> calls = rollCallRepository.findAllBySessionIdAndIsDeletedFalse(session.getId()).stream()
                    .filter(c -> liveLessonIds.contains(c.getLesson().getId()))
                    .collect(Collectors.toMap(c -> c.getId(), c -> c));
            livesTotal = calls.size();
            if (!calls.isEmpty()) {
                livesPresent = (int) attendanceRepository.findAllByRollCallIdIn(calls.keySet()).stream()
                        .filter(a -> a.isPresent() && a.getLearner().getId().equals(learner.getId())).count();
            }
        }

        var conditions = CertificateConditions.evaluate(config,
                new CertificateConditions.Facts(lessons, completed, passedQuizzes, livesTotal, livesPresent, project));
        // Une formation sans programme n'ouvre droit à aucun certificat (toutes les conditions seraient « remplies » à vide)
        return new Evaluation(conditions, !lessons.isEmpty() && CertificateConditions.allMet(conditions), includesProject);
    }
}
