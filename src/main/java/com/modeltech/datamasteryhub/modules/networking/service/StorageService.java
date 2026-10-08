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

    /** Lien de téléchargement temporaire (valable {@code validMinutes}) vers un objet privé. */
    String presignedGetUrl(String objectKey, int validMinutes);

    void delete(String objectKey);

    public record UploadResult(String objectKey, String url) {}
}
