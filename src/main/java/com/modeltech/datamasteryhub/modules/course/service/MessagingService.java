package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.MessagingPayloads;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Communication avec les apprenants : rappels de live automatiques, messages de l'équipe à une session,
 * questions des apprenants et réponses. Les envois passent par {@code MessageDispatcher} (canaux extensibles).
 */
public interface MessagingService {

    // ── Rappels de live ──────────────────────────────────────────────

    /** Envoie les rappels dus à l'instant {@code now} (24 h puis 1 h avant chaque live) ; retourne le nombre de messages envoyés. */
    int sendDueLiveReminders(LocalDateTime now);

    // ── Messages à une session ───────────────────────────────────────

    MessagingPayloads.SessionMessageResponse sendToSession(UUID sessionId, MessagingPayloads.SessionMessageRequest request,
                                                           String actorEmail, Collection<String> actorRoles);

    List<MessagingPayloads.SessionMessageResponse> history(UUID sessionId, String actorEmail, Collection<String> actorRoles);

    // ── Questions ────────────────────────────────────────────────────

    MessagingPayloads.LearnerQuestion ask(String learnerEmail, UUID lessonId, String question);

    List<MessagingPayloads.LearnerQuestion> myQuestions(String learnerEmail, UUID lessonId);

    List<MessagingPayloads.SessionQuestion> sessionQuestions(UUID sessionId, boolean onlyOpen,
                                                             String actorEmail, Collection<String> actorRoles);

    MessagingPayloads.SessionQuestion answer(UUID sessionId, UUID questionId, String answer,
                                             String actorEmail, Collection<String> actorRoles);
}
