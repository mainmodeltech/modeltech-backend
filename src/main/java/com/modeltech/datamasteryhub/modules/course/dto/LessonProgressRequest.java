package com.modeltech.datamasteryhub.modules.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Avancement d'une leçon (le front envoie {@code completed} et {@code positionSeconds}). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LessonProgressRequest {

    @NotNull(message = "completed est obligatoire")
    private Boolean completed;

    @NotNull(message = "positionSeconds est obligatoire")
    @Min(value = 0, message = "positionSeconds doit être positif")
    private Integer positionSeconds;
}
