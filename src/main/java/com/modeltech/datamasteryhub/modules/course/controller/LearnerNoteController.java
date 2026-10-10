package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.modules.course.dto.LessonNotePayloads;
import com.modeltech.datamasteryhub.modules.course.service.LessonNoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Notes personnelles de l'apprenant, synchronisées entre ses appareils (JSON brut, comme {@code /learner/**}). */
@RestController
@RequestMapping("/api/v1/learner/lessons/{lessonId}/note")
@RequiredArgsConstructor
@Tag(name = "Apprenant - Notes")
public class LearnerNoteController {

    private final LessonNoteService noteService;

    @GetMapping
    public LessonNotePayloads.Note get(@PathVariable UUID lessonId, Authentication authentication) {
        return noteService.get(authentication.getName(), lessonId);
    }

    @PutMapping
    public LessonNotePayloads.Note save(@PathVariable UUID lessonId,
                                        @Valid @RequestBody LessonNotePayloads.SaveRequest request,
                                        Authentication authentication) {
        return noteService.save(authentication.getName(), lessonId, request.getContent());
    }
}
