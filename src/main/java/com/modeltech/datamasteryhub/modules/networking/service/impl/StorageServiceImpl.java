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
        String extension = validatedImageExtension(file);   // taille, signature réelle du fichier ; l'extension vient du contenu
        String objectKey = folder + "/" + UUID.randomUUID() + "." + extension;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentTypeOf(extension))
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


    @Override
    public UploadResult uploadDocument(MultipartFile file, String folder, java.util.Set<String> allowedExtensions, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Le fichier est vide ou absent.");
        }
        if (file.getSize() > maxBytes) {
            throw new StorageException("Fichier trop volumineux (max " + maxBytes / (1024 * 1024) + " Mo).");
        }
        String name = file.getOriginalFilename();
        String extension = name != null && name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!allowedExtensions.contains(extension)) {
            throw new StorageException("Extension non autorisée : ." + extension
                    + " (autorisées : " + String.join(", ", new java.util.TreeSet<>(allowedExtensions)) + ").");
        }
        String objectKey = folder + "/" + UUID.randomUUID() + "." + extension;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            log.info("Document uploadé : {}", objectKey);
            return new UploadResult(objectKey, null);
        } catch (Exception e) {
            log.error("Erreur upload MinIO [{}] : {}", objectKey, e.getMessage(), e);
            throw new StorageException("Impossible d'uploader le fichier : " + e.getMessage());
        }
    }

    private static final long PRIVATE_IMAGE_MAX_BYTES = 5L * 1024 * 1024;

    @Override
    public UploadResult uploadPrivateImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Le fichier est vide ou absent.");
        }
        if (file.getSize() > PRIVATE_IMAGE_MAX_BYTES) {
            throw new StorageException("Fichier trop volumineux (max 5 Mo).");
        }
        String extension;
        try {
            extension = imageExtension(file.getBytes());
        } catch (java.io.IOException e) {
            throw new StorageException("Impossible de lire le fichier.");
        }
        if (extension == null) {
            throw new StorageException("Seules les images JPEG, PNG, WebP ou GIF sont autorisées.");
        }
        String objectKey = folder + "/" + UUID.randomUUID() + "." + extension;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            log.info("Image privée uploadée : {}", objectKey);
            return new UploadResult(objectKey, null);
        } catch (Exception e) {
            log.error("Erreur upload MinIO [{}] : {}", objectKey, e.getMessage(), e);
            throw new StorageException("Impossible d'uploader le fichier.");
        }
    }

    /** Extension déduite de la signature du fichier (JPEG, PNG, WebP, GIF) ; null si ce n'est pas une de ces images. */
    static String imageExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') return "gif";
        return null;
    }

    /** Client de signature, créé une fois : en construire un par lien alourdit inutilement les listes. */
    private volatile MinioClient signer;

    private MinioClient signer() {
        MinioClient s = signer;
        if (s == null) {
            synchronized (this) {
                if (signer == null) {
                    // Le lien est signé pour l'adresse publique de MinIO (celle que voit le navigateur).
                    // La région est fixée pour éviter tout appel réseau au moment de signer.
                    signer = MinioClient.builder()
                            .endpoint(minioProperties.getEffectivePublicUrl())
                            .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                            .region("us-east-1")
                            .build();
                }
                s = signer;
            }
        }
        return s;
    }

    @Override
    public String presignedGetUrl(String objectKey, int validMinutes) {
        try {
            return signer().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .expiry(validMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Erreur lien signé MinIO [{}] : {}", objectKey, e.getMessage());
            throw new StorageException("Impossible de générer le lien de téléchargement.");
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

    /** Type de contenu servi pour une extension d'image détectée. */
    private static String contentTypeOf(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "image/jpeg";
        };
    }

    /** SVG « inerte » : balise svg, sans script ni gestionnaire d'événement ni objet externe. */
    static boolean isSafeSvg(byte[] b) {
        String head = new String(b, 0, Math.min(b.length, 200_000), java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
        String trimmed = head.stripLeading().replaceFirst("^\\ufeff", "");
        if (!trimmed.startsWith("<") || !trimmed.contains("<svg")) return false;
        return !(head.contains("<script") || head.contains("javascript:") || head.contains("<foreignobject")
                || head.contains("<iframe") || head.contains("<embed") || head.contains("<object")
                || java.util.regex.Pattern.compile("\\son[a-z]+\\s*=").matcher(head).find());
    }

    /** Vérifie taille et contenu réel de l'image ; retourne son extension (jamais celle du nom de fichier envoyé). */
    private String validatedImageExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Le fichier est vide ou absent.");
        }
        long maxSizeBytes = 5L * 1024 * 1024; // 5 Mo
        if (file.getSize() > maxSizeBytes) {
            throw new StorageException("Fichier trop volumineux (max 5 Mo, reçu : " + file.getSize() / 1024 + " Ko).");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new StorageException("Impossible de lire le fichier.");
        }
        String extension = imageExtension(bytes);
        if (extension == null && isSafeSvg(bytes)) extension = "svg";
        if (extension == null) {
            throw new StorageException("Seules les images JPEG, PNG, WebP, GIF ou SVG sont autorisées.");
        }
        return extension;
    }

    @SuppressWarnings("unused")
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

}
