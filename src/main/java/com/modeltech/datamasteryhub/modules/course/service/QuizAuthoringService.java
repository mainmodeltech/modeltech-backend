package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;

import java.util.Collection;
import java.util.UUID;

/** Banque de questions d'une leçon QUIZ, éditée depuis le back-office. */
public interface QuizAuthoringService {

    EvaluationPayloads.QuizBank get(UUID lessonId, String actorEmail, Collection<String> actorRoles);

    /** Questions d'une leçon (sans contrôle d'accès : l'appelant l'a fait) ; vide si la leçon n'existe pas. */
    java.util.List<EvaluationPayloads.QuizQuestionEdit> questionsOf(UUID lessonId);

    /** Règles d'une banque (énoncé, 2 à 8 réponses, une seule bonne, identifiants uniques), sans écriture. */
    void validateQuestions(java.util.List<EvaluationPayloads.QuizQuestionEdit> questions);

    /** Remplace les questions par la liste fournie (identifiants inconnus = nouvelles questions) et retourne le résultat enregistré. */
    EvaluationPayloads.QuizBank save(UUID lessonId, EvaluationPayloads.QuizBank bank,
                                     String actorEmail, Collection<String> actorRoles);
}
