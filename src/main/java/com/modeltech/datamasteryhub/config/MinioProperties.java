package com.modeltech.datamasteryhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Propriétés MinIO lues depuis application.yml (préfixe "minio").
 *
 * Exemple dans application.yml :
 *   minio:
 *     endpoint:   ${MINIO_ENDPOINT:http://minio:9000}
 *     access-key: ${MINIO_ROOT_USER}
 *     secret-key: ${MINIO_ROOT_PASSWORD}
 *     bucket:     ${MINIO_BUCKET:media}
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    @NotBlank(message = "minio.endpoint est obligatoire")
    private String endpoint;

    /**
     * URL publique de MinIO accessible depuis le navigateur.
     * Si non définie, utilise endpoint comme fallback.
     * Ex prod : https://minio.model-technologie.com
     * Ex dev  : http://localhost:9000
     */
    private String publicUrl;

    @NotBlank(message = "minio.access-key est obligatoire")
    private String accessKey;

    @NotBlank(message = "minio.secret-key est obligatoire")
    private String secretKey;

    @NotBlank(message = "minio.bucket est obligatoire")
    private String bucket;

    /** Retourne publicUrl si défini, sinon endpoint. */
    public String getEffectivePublicUrl() {
        return (publicUrl != null && !publicUrl.isBlank()) ? publicUrl : endpoint;
    }
}
