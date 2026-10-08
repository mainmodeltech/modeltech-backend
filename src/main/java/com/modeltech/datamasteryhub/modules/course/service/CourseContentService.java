package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.CourseContentPayload;

import java.util.Collection;
import java.util.UUID;

/** Édition du programme d'une formation par le back-office. */
public interface CourseContentService {

    /**
     * @param actorEmail  compte connecté
     * @param actorRoles  ses rôles (ex. {@code ROLE_ADMIN}) : le personnel édite toutes les formations,
     *                    un compte PARTNER uniquement celles de son partenaire
     */
    CourseContentPayload getAdminContent(UUID formationId, String actorEmail, Collection<String> actorRoles);

    /** Remplace le programme par l'arbre fourni et retourne le résultat enregistré (identifiants définitifs). */
    CourseContentPayload saveAdminContent(UUID formationId, CourseContentPayload payload,
                                          String actorEmail, Collection<String> actorRoles);
}
