package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;

import java.util.UUID;

/** Vue d'ensemble des évaluations d'un apprenant et suivi de session du formateur. */
public interface EvaluationService {

    /** Quiz, projet final et conditions d'obtention du certificat, calculés par le serveur. */
    EvaluationPayloads.EvaluationsOverview getOverview(String learnerEmail, UUID formationId);

    EvaluationPayloads.SessionTracking getSessionTracking(UUID sessionId, String actorEmail, java.util.Collection<String> actorRoles);

    /** Enregistre l'appel d'un live : les apprenants listés sont présents, les autres absents. */
    EvaluationPayloads.SessionLive saveAttendance(UUID sessionId, UUID liveId,
                                                  EvaluationPayloads.AttendanceUpdate update, String actorEmail,
                                                  java.util.Collection<String> actorRoles);
}
