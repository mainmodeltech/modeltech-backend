package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.LearnerPayloads;
import com.modeltech.datamasteryhub.modules.course.dto.LessonProgressRequest;

import java.util.UUID;

/** Espace apprenant : tableau de bord, cours, progression. Chaque appel est limité aux accès de l'apprenant connecté. */
public interface LearnerSpaceService {

    LearnerPayloads.Dashboard getDashboard(String learnerEmail);

    /** @throws org.springframework.web.server.ResponseStatusException 403 sans accès, accès pas encore ouvert ou expiré */
    LearnerPayloads.LearnerCourse getCourse(String learnerEmail, UUID formationId);

    /** Enregistre l'avancement d'une leçon (vidéo reprise à la seconde près, leçon terminée). */
    void setLessonProgress(String learnerEmail, UUID lessonId, LessonProgressRequest request);
}
