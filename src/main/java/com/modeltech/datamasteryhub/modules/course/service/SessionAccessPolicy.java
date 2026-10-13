package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;

/**
 * Qui peut suivre une session (appel, correction, certificats) : qui a le droit {@code SCOPE_ALL_SESSIONS} (pédagogie,
 * direction) pour toutes ; sinon uniquement le formateur à qui elle est confiée. Le droit d'agir sur les sessions
 * ({@code SESSIONS_VIEW} / {@code SESSIONS_TRACK}) est déjà contrôlé par la route.
 */
@Component
public class SessionAccessPolicy {

    public void require(BootcampSession session, String actorEmail, Collection<String> actorAuthorities) {
        if (actorAuthorities.contains(Permission.SCOPE_ALL_SESSIONS.name())) return;

        boolean assigned = session.getTrainer() != null && session.getTrainer().getEmail().equalsIgnoreCase(actorEmail);
        if (assigned) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette session ne vous est pas confiée.");
    }
}
