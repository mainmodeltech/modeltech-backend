package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.course.entity.CourseConfig;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.LessonProgress;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonProgressRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Règles d'accès de l'espace apprenant, partagées par le cours, les évaluations et le projet :
 * le serveur est seul juge de ce que l'apprenant a le droit d'ouvrir.
 */
@Component
@RequiredArgsConstructor
public class LearnerAccess {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LearnerRepository learnerRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseContentAssembler assembler;

    @Value("${app.timezone:Africa/Dakar}")
    private String timezone;

    public Learner requireLearner(String email) {
        return learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                .orElseThrow(() -> forbidden("Espace réservé aux apprenants."));
    }

    /** Accès de l'apprenant à la formation : inscription en cours, ouverte (date de début) et non expirée. */
    public Enrollment requireAccess(Learner learner, UUID formationId) {
        Enrollment enrollment = enrollmentRepository.findAccessibleByLearner(learner.getId()).stream()
                .filter(e -> e.getRegistration().getBootcamp().getId().equals(formationId))
                .findFirst()
                .orElseThrow(() -> forbidden("Vous n'avez pas accès à cette formation."));

        CourseConfig config = assembler.configOf(enrollment.getRegistration().getBootcamp());
        LocalDate today = today();
        if (enrollment.getAccessStartsAt() != null && today.isBefore(enrollment.getAccessStartsAt())) {
            throw forbidden("Votre accès s'ouvre le " + DAY.format(enrollment.getAccessStartsAt()) + ".");
        }
        if (CourseConfig.ACCESS_12_MONTHS.equals(config.getAccessDuration())) {
            LocalDate reference = enrollment.getAccessEndsAt() != null
                    ? enrollment.getAccessEndsAt() : enrollment.getAccessStartsAt();
            if (reference != null && today.isAfter(reference.plusMonths(12))) {
                throw forbidden("Votre accès à cette formation a expiré.");
            }
        }
        return enrollment;
    }

    /** Déblocage séquentiel : un module n'est ouvert que si toutes les leçons publiées du précédent sont terminées. */
    public boolean isModuleLocked(Learner learner, CourseLesson target) {
        Bootcamp bootcamp = target.getModule().getBootcamp();
        if (!assembler.configOf(bootcamp).isSequentialUnlock()) return false;

        Set<UUID> completed = progressRepository.findAllByLearnerIdAndIsDeletedFalse(learner.getId()).stream()
                .filter(LessonProgress::isCompleted).map(p -> p.getLesson().getId()).collect(Collectors.toSet());
        Map<UUID, List<CourseLesson>> byModule = lessonRepository.findAllByBootcampIds(List.of(bootcamp.getId())).stream()
                .filter(l -> l.getStatus() != LessonStatus.DRAFT)
                .collect(Collectors.groupingBy(l -> l.getModule().getId(), LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<UUID, List<CourseLesson>> module : byModule.entrySet()) {
            if (module.getKey().equals(target.getModule().getId())) return false;
            if (!module.getValue().stream().allMatch(l -> completed.contains(l.getId()))) return true;
        }
        return false;
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.of(timezone));
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
