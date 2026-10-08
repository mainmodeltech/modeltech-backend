package com.modeltech.datamasteryhub.modules.notification.service;

/**
 * Données de l'e-mail « votre certificat est prêt » (valeurs simples : envoi asynchrone hors transaction).
 *
 * @param pdf         le certificat en pièce jointe
 * @param linkedInUrl lien d'ajout au profil LinkedIn
 */
public record CertificateNotice(
        String to,
        String firstName,
        String formationTitle,
        String publicId,
        String verifyUrl,
        String linkedInUrl,
        byte[] pdf) {}
