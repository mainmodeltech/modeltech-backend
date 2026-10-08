package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Set;

/** Qui peut éditer le contenu pédagogique d'une formation : le personnel, et un partenaire pour SES formations. */
@Component
@RequiredArgsConstructor
public class CourseAccessPolicy {

    private static final Set<String> STAFF_ROLES = Set.of(RoleNames.SUPER_ADMIN, RoleNames.ADMIN, RoleNames.EDITOR);

    private final AdminUserRepository adminUserRepository;

    public void requireEditable(Bootcamp bootcamp, String actorEmail, Collection<String> actorRoles) {
        if (actorRoles.stream().anyMatch(STAFF_ROLES::contains)) return;

        if (actorRoles.contains(RoleNames.PARTNER)) {
            AdminUser actor = adminUserRepository.findByEmailAndIsDeletedFalse(actorEmail).orElse(null);
            boolean ownsIt = actor != null && actor.getPartner() != null && bootcamp.getPartner() != null
                    && bootcamp.getPartner().getId().equals(actor.getPartner().getId());
            if (ownsIt) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Vous ne pouvez pas modifier le contenu pédagogique de cette formation.");
    }
}
