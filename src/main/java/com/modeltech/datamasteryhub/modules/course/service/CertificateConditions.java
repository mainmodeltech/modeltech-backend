package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseConfig;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.enums.ProjectStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Conditions d'obtention du certificat, calculées à partir des règles de la formation
 * ({@link CourseConfig}) et de ce que l'apprenant a accompli. Source unique : l'écran d'évaluations de
 * l'apprenant, le suivi de session et la délivrance automatique lisent la même règle.
 */
public final class CertificateConditions {

    private CertificateConditions() {}

    /**
     * @param lessons         leçons non brouillon de la formation
     * @param requiredProject statut du projet final si la formation l'exige, sinon null
     */
    public record Facts(List<CourseLesson> lessons, Set<UUID> completedLessons, Set<UUID> passedQuizLessons,
                        int livesTotal, int livesPresent, ProjectStatus requiredProject) {}

    public static List<EvaluationPayloads.CertificateCondition> evaluate(CourseConfig config, Facts f) {
        List<EvaluationPayloads.CertificateCondition> out = new ArrayList<>();

        int lessonsTotal = f.lessons().size();
        int lessonsDone = (int) f.lessons().stream().filter(l -> f.completedLessons().contains(l.getId())).count();
        int lessonsPercent = percent(lessonsDone, lessonsTotal);
        out.add(condition("LESSONS", "Leçons terminées", lessonsDone + " / " + lessonsTotal, lessonsPercent,
                lessonsTotal == 0 || lessonsPercent >= config.getLessonsCompletedPercent()));

        int quizTotal = (int) f.lessons().stream().filter(l -> l.getType() == LessonType.QUIZ).count();
        int quizPassed = (int) f.lessons().stream()
                .filter(l -> l.getType() == LessonType.QUIZ && f.passedQuizLessons().contains(l.getId())).count();
        out.add(condition("QUIZZES", "Quiz réussis", quizPassed + " / " + quizTotal, percent(quizPassed, quizTotal),
                quizPassed == quizTotal));

        int livePercent = percent(f.livesPresent(), f.livesTotal());
        out.add(condition("LIVES", "Présence aux lives", f.livesPresent() + " / " + f.livesTotal(), livePercent,
                f.livesTotal() == 0 || livePercent >= config.getLivePresencePercent()));

        if (f.requiredProject() != null) {
            boolean validated = f.requiredProject() == ProjectStatus.VALIDATED;
            out.add(condition("PROJECT", "Projet final", projectLabel(f.requiredProject()), validated ? 100 : 0, validated));
        }
        return out;
    }

    public static boolean allMet(List<EvaluationPayloads.CertificateCondition> conditions) {
        return conditions.stream().allMatch(EvaluationPayloads.CertificateCondition::isMet);
    }

    private static EvaluationPayloads.CertificateCondition condition(String key, String label, String value, int percent, boolean met) {
        return EvaluationPayloads.CertificateCondition.builder()
                .key(key).label(label).valueLabel(value).percent(percent).met(met).build();
    }

    private static String projectLabel(ProjectStatus s) {
        return switch (s) {
            case NOT_STARTED -> "Non rendu";
            case SUBMITTED -> "En correction";
            case CHANGES_REQUESTED -> "Corrections demandées";
            case VALIDATED -> "Validé";
        };
    }

    private static int percent(int part, int total) {
        return total == 0 ? 100 : (int) Math.round(part * 100.0 / total);
    }
}
