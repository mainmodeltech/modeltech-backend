package com.modeltech.datamasteryhub.modules.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Programme d'une formation. Mêmes noms de champs que {@code CourseContent} côté front
 * (src/types/course.type.ts) : lu et écrit en bloc (arbre complet).
 *
 * <p>Les identifiants reçus peuvent être temporaires (nouvel élément créé dans l'éditeur) :
 * ceux qui ne correspondent à aucun élément existant de la formation sont créés, et la réponse
 * porte les identifiants définitifs.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseContentPayload {

    private String formationId;
    /** Titre de la formation (lecture seule : il se modifie dans la fiche formation). */
    private String title;
    /** Dernière sauvegarde du programme ; null si jamais enregistré. */
    private LocalDateTime updatedAt;
    private Settings settings;
    private CertificateRules certificateRules;
    @Builder.Default
    private List<ModuleItem> modules = new ArrayList<>();

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Settings {
        private Boolean sequentialUnlock;
        /** {@code 12_MONTHS} ou {@code LIFETIME}. */
        private String accessDuration;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CertificateRules {
        private Integer lessonsCompletedPercent;
        private Integer quizPassPercent;
        private Integer livePresencePercent;
        private Boolean finalProjectValidated;
        private String template;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModuleItem {
        private String id;
        /** Rang (à partir de 1) ; en écriture, l'ordre du tableau fait foi. */
        private Integer order;
        private String title;
        @Builder.Default
        private List<LessonItem> lessons = new ArrayList<>();
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LessonItem {
        private String id;
        private Integer order;
        private String title;
        private String subtitle;
        private LessonType type;
        private LessonStatus status;
        private Integer durationSeconds;
        private String videoProviderId;
        private String videoUrl;
        private String description;
        /** Heure locale ISO (ex. {@code 2026-11-14T18:00:00}) ; un décalage horaire est accepté en entrée. */
        private String liveAt;
        private String liveUrl;
        private Quiz quiz;
        @Builder.Default
        private List<ResourceItem> resources = new ArrayList<>();
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quiz {
        private Integer questionCount;
        private Integer passThreshold;
        /** Nulle = tentatives illimitées. */
        private Integer maxAttempts;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceItem {
        private String id;
        private String name;
        private String fileType;
        private String sizeLabel;
        private String note;
        private String url;
        private Boolean lockedUntilQuiz;
    }
}
