package com.modeltech.datamasteryhub.modules.notification.service;

import java.util.List;

/**
 * Données des e-mails d'une proposition de modification du programme (valeurs simples : envoi asynchrone hors transaction).
 *
 * @param to       relecteurs (soumission) ou auteur (décision) ; vide = adresse de l'équipe
 * @param decision {@code SUBMITTED}, {@code APPROVED}, {@code CHANGES_REQUESTED} ou {@code REJECTED}
 * @param comment  commentaire de la pédagogie (décision), sinon nul
 */
public record ProgramProposalNotice(
        List<String> to,
        String formationTitle,
        String authorName,
        String reviewerName,
        String decision,
        String comment) {}
