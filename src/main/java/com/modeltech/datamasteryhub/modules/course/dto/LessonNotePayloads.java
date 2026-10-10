package com.modeltech.datamasteryhub.modules.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Notes personnelles d'un apprenant sur une leçon. */
public final class LessonNotePayloads {

    private LessonNotePayloads() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Note {
        private String lessonId;
        /** Vide tant que l'apprenant n'a rien écrit. */
        private String content;
        private LocalDateTime updatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveRequest {
        @NotNull(message = "Le contenu est obligatoire (vide pour effacer la note)")
        @Size(max = 10000, message = "10 000 caractères maximum")
        private String content;
    }
}
