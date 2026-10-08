package com.modeltech.datamasteryhub.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Réponses de l'espace apprenant — mêmes noms que {@code course.type.ts}. */
public final class LearnerPayloads {

    private LearnerPayloads() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LessonProgressItem {
        private String lessonId;
        private boolean completed;
        private int positionSeconds;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LearnerCourse {
        private CourseContentPayload content;
        private String cohortLabel;
        private String trainerName;
        private List<LessonProgressItem> progress;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EnrollmentSummary {
        private String formationId;
        private String title;
        private String providerLabel;
        private int progressPercent;
        private String statusNote;
        /** IN_PROGRESS, CERTIFIED ou UPCOMING. */
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TodoItem {
        private String id;
        private String title;
        private String subtitle;
        private String badge;
        /** warning ou info. */
        private String tone;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LiveItem {
        private String id;
        private String startsAt;
        private String title;
        private String timeLabel;
        private String place;
        private String joinUrl;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResumeInfo {
        private String formationId;
        private String lessonId;
        private String courseTitle;
        /** À partir de 1. */
        private int moduleIndex;
        private int moduleCount;
        private String lessonTitle;
        private String lessonLabel;
        private int durationSeconds;
        private int positionSeconds;
        private int percent;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Stats {
        private double hoursWatched;
        private int lessonsDone;
        private int lessonsTotal;
        private Integer averageQuizScore;
        private int certificates;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CertificateReady {
        private String title;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Dashboard {
        private String firstName;
        private Integer streakDays;
        private Stats stats;
        private ResumeInfo resume;
        private List<EnrollmentSummary> courses;
        private List<TodoItem> todos;
        private CertificateReady certificateReady;
        private List<LiveItem> lives;
    }
}
