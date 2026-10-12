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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Pièces privées (justificatifs de paiement) : le contenu décide du type, jamais le nom ni l'en-tête du client. */
class StoragePrivateImageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

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
    void storesARealImage_underARandomKey_withTheExtensionOfItsContent_asOpaqueBinary() throws Exception {
        // nom et type annoncés mensongers : seul le contenu compte
        MockMultipartFile file = new MockMultipartFile("file", "recu.html", "text/html", PNG);

        StorageService.UploadResult result = storage.uploadPrivateImage(file, "payment-proofs");

        assertThat(result.objectKey()).startsWith("payment-proofs/").endsWith(".png").doesNotContain("recu");
        assertThat(result.url()).isNull();
        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(args.capture());
        assertThat(args.getValue().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void acceptsJpeg() {
        StorageService.UploadResult result = storage.uploadPrivateImage(
                new MockMultipartFile("file", "photo", "image/jpeg", JPEG), "payment-proofs");
        assertThat(result.objectKey()).endsWith(".jpg");
    }

    @Test
    void refusesHtmlSvgScriptsAndEmptyOrOversizedFiles_beforeTouchingTheStorage() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes();

        // un contenu non image est refusé même s'il se présente comme une image
        assertThatThrownBy(() -> storage.uploadPrivateImage(new MockMultipartFile("file", "a.png", "image/png", html), "p"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.uploadPrivateImage(new MockMultipartFile("file", "a.svg", "image/svg+xml", svg), "p"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.uploadPrivateImage(new MockMultipartFile("file", "a.png", "image/png", new byte[0]), "p"))
                .isInstanceOf(StorageException.class);
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> storage.uploadPrivateImage(new MockMultipartFile("file", "a.png", "image/png", big), "p"))
                .isInstanceOf(StorageException.class).hasMessageContaining("volumineux");

        verify(minio, never()).putObject(org.mockito.ArgumentMatchers.any(PutObjectArgs.class));
    }
}
