package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;

import java.util.UUID;

/**
 * Passage d'un quiz par un apprenant. Le serveur tire les questions, corrige, compte les
 * tentatives et décide de la réussite : le client n'envoie que ses réponses.
 */
public interface QuizAttemptService {

    /** Démarre (ou reprend, si une tentative est ouverte) une tentative. {@code quizId} est l'identifiant de la leçon QUIZ. */
    EvaluationPayloads.QuizAttemptStart start(String learnerEmail, UUID quizId);

    /** Corrige la tentative. Une réussite marque la leçon comme terminée. */
    EvaluationPayloads.QuizAttemptResult submit(String learnerEmail, UUID attemptId, EvaluationPayloads.QuizSubmission submission);
}
