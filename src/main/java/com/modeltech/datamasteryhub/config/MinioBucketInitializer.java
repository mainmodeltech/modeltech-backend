package com.modeltech.datamasteryhub.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Crée le bucket MinIO s'il n'existe pas (lecture publique des objets, comme en production).
 * Sans lui, tout envoi de fichier échoue avec « The specified bucket does not exist ».
 * N'écrase jamais la configuration d'un bucket existant ; un échec est journalisé sans bloquer le démarrage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MinioBucketInitializer {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        String bucket = minioProperties.getBucket();
        try {
            if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                return;
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                    + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            log.info("Bucket MinIO « {} » créé (lecture publique)", bucket);
        } catch (Exception e) {
            log.warn("Impossible de vérifier/créer le bucket MinIO « {} » : {}", bucket, e.getMessage());
        }
    }
}
