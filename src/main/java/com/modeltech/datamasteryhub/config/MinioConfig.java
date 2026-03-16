package com.modeltech.datamasteryhub.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crée et expose le bean {@link MinioClient} utilisé par {@code StorageService}.
 *
 * MinIO n'a pas d'auto-configuration Spring Boot — cette classe est obligatoire.
 * Elle lit les propriétés via {@link MinioProperties} (@ConfigurationProperties).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        log.info("Initialisation du client MinIO → endpoint={}, bucket={}",
                minioProperties.getEndpoint(), minioProperties.getBucket());

        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
