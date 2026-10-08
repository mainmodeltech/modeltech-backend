package com.modeltech.datamasteryhub.modules.networking;

import com.modeltech.datamasteryhub.config.MinioProperties;
import com.modeltech.datamasteryhub.exception.StorageException;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.networking.service.impl.StorageServiceImpl;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Dépôt de documents (rendus de projet) : extension, taille et type de contenu sont décidés par le serveur. */
class StorageDocumentUploadTest {

    private static final Set<String> ALLOWED = Set.of("pbix", "xlsx");

    private MinioClient minio;
    private StorageService storage;

    @BeforeEach
    void setUp() {
        minio = mock(MinioClient.class);
        MinioProperties props = new MinioProperties();
        props.setEndpoint("http://minio:9000");
        props.setAccessKey("k");
        props.setSecretKey("s");
        props.setBucket("media");
        storage = new StorageServiceImpl(minio, props);
    }

    @Test
    void storesAnAllowedDocumentUnderARandomKey_asOpaqueBinary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "Rapport Final.PBIX", "text/html", new byte[100]);

        StorageService.UploadResult result = storage.uploadDocument(file, "projects/abc", ALLOWED, 1024);

        assertThat(result.objectKey()).startsWith("projects/abc/").endsWith(".pbix").doesNotContain("Rapport");
        assertThat(result.url()).isNull();
        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(args.capture());
        assertThat(args.getValue().contentType()).isEqualTo("application/octet-stream");   // jamais le type annoncé par le client
    }

    @Test
    void refusesOtherExtensions_oversizedAndEmptyFiles_beforeTouchingTheStorage() throws Exception {
        assertThatThrownBy(() -> storage.uploadDocument(
                new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[10]), "p", ALLOWED, 1024))
                .isInstanceOf(StorageException.class).hasMessageContaining(".exe");
        assertThatThrownBy(() -> storage.uploadDocument(
                new MockMultipartFile("file", "sans-extension", "application/octet-stream", new byte[10]), "p", ALLOWED, 1024))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.uploadDocument(
                new MockMultipartFile("file", "gros.pbix", "application/octet-stream", new byte[2048]), "p", ALLOWED, 1024))
                .isInstanceOf(StorageException.class).hasMessageContaining("volumineux");
        assertThatThrownBy(() -> storage.uploadDocument(
                new MockMultipartFile("file", "vide.pbix", "application/octet-stream", new byte[0]), "p", ALLOWED, 1024))
                .isInstanceOf(StorageException.class);

        verify(minio, never()).putObject(org.mockito.ArgumentMatchers.any(PutObjectArgs.class));
    }

    @Test
    void presignedLinkTargetsThePublicAddress_withoutNetworkAccess() {
        MinioProperties props = new MinioProperties();
        props.setEndpoint("http://minio:9000");
        props.setPublicUrl("https://files.example.com");
        props.setAccessKey("k");
        props.setSecretKey("s");
        props.setBucket("media");

        String url = new StorageServiceImpl(minio, props).presignedGetUrl("projects/abc/x.pbix", 15);

        assertThat(url).startsWith("https://files.example.com/media/projects/abc/x.pbix?").contains("X-Amz-Signature=");
    }
}
