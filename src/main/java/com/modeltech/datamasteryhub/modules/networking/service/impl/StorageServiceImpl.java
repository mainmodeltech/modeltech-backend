package com.modeltech.datamasteryhub.modules.networking.service.impl;

import com.modeltech.datamasteryhub.config.MinioProperties;
import com.modeltech.datamasteryhub.exception.StorageException;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import io.minio.*;
import io.minio.http.Method;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction MinIO pour upload et suppression de fichiers.
 *
 * Nommage des objets : {folder}/{uuid}.{extension}
 * Ex: "alumni/a3c7f.jpg", "projects/screenshots/b9d2e.png"
 */
@Service
@RequiredArgsConstructor
@Slf4j

public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.endpoint}")
    private String endpoint;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Upload un fichier dans le bucket et retourne l'URL publique.
     *
     * @param file   fichier à uploader
     * @param folder sous-dossier dans le bucket (ex: "alumni", "projects/screenshots")
     * @return résultat contenant l'objectKey et l'URL publique
     */
    public UploadResult upload(MultipartFile file, String folder) {
        validateFile(file);

        String extension = extractExtension(file.getOriginalFilename());
        String objectKey = folder + "/" + UUID.randomUUID() + "." + extension;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            String url = buildPublicUrl(objectKey);
            log.info("Fichier uploadé : {} → {}", objectKey, url);
            return new UploadResult(objectKey, url);

        } catch (Exception e) {
            log.error("Erreur upload MinIO [{}] : {}", objectKey, e.getMessage(), e);
            throw new StorageException("Impossible d'uploader le fichier : " + e.getMessage());
        }
    }


    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Supprime un objet du bucket via sa clé.
     * Silencieux si l'objet n'existe plus (idempotent).
     */
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build()
            );
            log.info("Fichier supprimé de MinIO : {}", objectKey);
        } catch (Exception e) {
            // Best-effort : la suppression BDD ne doit pas être bloquée
            log.warn("Impossible de supprimer l'objet MinIO [{}] : {}", objectKey, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Le fichier est vide ou absent.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new StorageException("Seules les images sont autorisées (reçu : " + contentType + ").");
        }
        long maxSizeBytes = 5L * 1024 * 1024; // 5 Mo
        if (file.getSize() > maxSizeBytes) {
            throw new StorageException("Fichier trop volumineux (max 5 Mo, reçu : "
                    + file.getSize() / 1024 + " Ko).");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String buildPublicUrl(String objectKey) {
        // Utilise publicUrl (accessible browser) et non endpoint (réseau interne Docker)
        String base = minioProperties.getEffectivePublicUrl().replaceAll("/+$", "");
        return base + "/" + minioProperties.getBucket() + "/" + objectKey;
    }

    /** Exception métier levée en cas d'erreur de stockage */
    public static class StorageException extends RuntimeException {
        public StorageException(String message) { super(message); }
    }



}
