package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;

/**
 * Qui peut suivre une session (appel, correction, certificats) : l'administration pour toutes,
 * un formateur uniquement pour celles qui lui sont confiées.
 */
@Component
public class SessionAccessPolicy {

    public void require(BootcampSession session, String actorEmail, Collection<String> actorRoles) {
        if (actorRoles.contains(RoleNames.SUPER_ADMIN) || actorRoles.contains(RoleNames.ADMIN)) return;

        if (actorRoles.contains(RoleNames.TRAINER)) {
            boolean assigned = session.getTrainer() != null && session.getTrainer().getEmail().equalsIgnoreCase(actorEmail);
            if (assigned) return;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette session ne vous est pas confiée.");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès réservé à l'équipe pédagogique.");
    }
}
