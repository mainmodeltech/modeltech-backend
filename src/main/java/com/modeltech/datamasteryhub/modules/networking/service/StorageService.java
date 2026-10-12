package com.modeltech.datamasteryhub.modules.networking.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    UploadResult upload(MultipartFile file, String folder);

    /**
     * Dépose un document (rendu de projet…) sous une clé aléatoire. Contrairement à {@link #upload},
     * accepte les extensions listées (pas seulement des images) ; le fichier est stocké en
     * {@code application/octet-stream} pour qu'un navigateur ne l'interprète jamais. Le résultat
     * n'a pas d'URL publique : utiliser {@link #presignedGetUrl}.
     */
    UploadResult uploadDocument(MultipartFile file, String folder, java.util.Set<String> allowedExtensions, long maxBytes);

    /**
     * Dépose une image à caractère privé (justificatif de paiement…) : le contenu doit être réellement
     * une image JPEG, PNG, WebP ou GIF (vérifié sur les premiers octets, pas sur le type annoncé par le
     * client), 5 Mo maximum. Stockée en {@code application/octet-stream} sous une clé aléatoire, sans URL
     * publique : le bucket n'autorise la lecture anonyme que sur certains dossiers (voir
     * {@code docs/ops/phase-0-securite.md}) ; utiliser {@link #presignedGetUrl}.
     */
    UploadResult uploadPrivateImage(MultipartFile file, String folder);

    /** Lien de téléchargement temporaire (valable {@code validMinutes}) vers un objet privé. */
    String presignedGetUrl(String objectKey, int validMinutes);

    void delete(String objectKey);

    public record UploadResult(String objectKey, String url) {}
}
