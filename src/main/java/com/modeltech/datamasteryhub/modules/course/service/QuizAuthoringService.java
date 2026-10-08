package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;

import java.util.Collection;
import java.util.UUID;

/** Banque de questions d'une leçon QUIZ, éditée depuis le back-office. */
public interface QuizAuthoringService {

    EvaluationPayloads.QuizBank get(UUID lessonId, String actorEmail, Collection<String> actorRoles);

    /** Remplace les questions par la liste fournie (identifiants inconnus = nouvelles questions) et retourne le résultat enregistré. */
    EvaluationPayloads.QuizBank save(UUID lessonId, EvaluationPayloads.QuizBank bank,
                                     String actorEmail, Collection<String> actorRoles);
}
