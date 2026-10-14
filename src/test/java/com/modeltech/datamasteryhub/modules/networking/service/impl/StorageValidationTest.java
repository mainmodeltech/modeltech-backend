package com.modeltech.datamasteryhub.modules.networking.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Les images sont reconnues à leur contenu réel (signature), pas au type ou au nom annoncés par le client. */
class StorageValidationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Test
    void imageExtension_followsTheRealSignature() {
        assertThat(StorageServiceImpl.imageExtension(PNG)).isEqualTo("png");
        assertThat(StorageServiceImpl.imageExtension(JPEG)).isEqualTo("jpg");
        assertThat(StorageServiceImpl.imageExtension("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEqualTo("gif");
        assertThat(StorageServiceImpl.imageExtension("RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).isEqualTo("webp");
        assertThat(StorageServiceImpl.imageExtension("<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(StorageServiceImpl.imageExtension("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(StorageServiceImpl.imageExtension(new byte[0])).isNull();
    }

    @Test
    void svg_isAcceptedOnlyWhenInert() {
        assertThat(StorageServiceImpl.isSafeSvg("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(StorageServiceImpl.isSafeSvg("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))).isTrue();

        for (String hostile : new String[]{
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><a href=\"javascript:alert(1)\"><rect/></a></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><foreignObject><iframe src=\"x\"/></foreignObject></svg>",
                "<html><svg/></html> <script>1</script>",
                "pas du tout un svg",
        }) {
            assertThat(StorageServiceImpl.isSafeSvg(hostile.getBytes(StandardCharsets.UTF_8))).as(hostile).isFalse();
        }
    }
}
