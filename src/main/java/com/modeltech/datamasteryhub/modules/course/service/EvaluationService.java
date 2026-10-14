package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;

import java.util.UUID;

/** Vue d'ensemble des évaluations d'un apprenant et suivi de session du formateur. */
public interface EvaluationService {

    /** Quiz, projet final et conditions d'obtention du certificat, calculés par le serveur. */
    EvaluationPayloads.EvaluationsOverview getOverview(String learnerEmail, UUID formationId);

    /** Quiz d'un apprenant d'une session : tentatives utilisées, limite effective, meilleur score (équipe). */
    java.util.List<EvaluationPayloads.QuizSummary> learnerQuizzes(UUID sessionId, UUID learnerId, String actorEmail,
                                                                  java.util.Collection<String> actorRoles);

    /** Accorde des tentatives supplémentaires à un apprenant sur un quiz (motif obligatoire, conservé). */
    EvaluationPayloads.QuizSummary grantAttempts(UUID sessionId, UUID learnerId, UUID quizId,
                                                 EvaluationPayloads.GrantAttemptsRequest request, String actorEmail,
                                                 java.util.Collection<String> actorRoles);

    EvaluationPayloads.SessionTracking getSessionTracking(UUID sessionId, String actorEmail, java.util.Collection<String> actorRoles);

    /** Enregistre l'appel d'un live : les apprenants listés sont présents, les autres absents. */
    EvaluationPayloads.SessionLive saveAttendance(UUID sessionId, UUID liveId,
                                                  EvaluationPayloads.AttendanceUpdate update, String actorEmail,
                                                  java.util.Collection<String> actorRoles);
}
