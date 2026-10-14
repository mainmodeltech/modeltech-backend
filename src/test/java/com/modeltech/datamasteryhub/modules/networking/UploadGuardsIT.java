package com.modeltech.datamasteryhub.modules.networking;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Téléversements : contenu réellement contrôlé, taille bornée tôt avec un message lisible (jamais un 500 opaque). */
class UploadGuardsIT extends AbstractIntegrationTest {

    @Autowired private PartnerRepository partnerRepository;

    private final RequestPostProcessor pedagogy = TestAuth.as("pedago@test.local", "PEDAGOGY");

    private String logoUrl() {
        Partner p = partnerRepository.save(TestData.partner("agile-upload", "Agile Upload"));
        return "/api/v1/admin/partners/" + p.getId() + "/logo";
    }

    @Test
    void aFileThatIsNotReallyAnImage_isRefused_whateverItsAnnouncedTypeOrName() throws Exception {
        String url = logoUrl();
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "logo.png", "image/png", "<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8))).with(pedagogy))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("images")));
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "page.html", "image/png", "<html><script>1</script></html>".getBytes(StandardCharsets.UTF_8))).with(pedagogy))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8))).with(pedagogy))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOversizedUpload_isRefusedEarlyWithAReadableMessage() throws Exception {
        String url = logoUrl();
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1})).with(pedagogy)
                        .header("Content-Length", 9L * 1024 * 1024))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("trop volumineux")));
    }

    @Test
    void anImageOverFiveMegabytes_isRefusedByTheStorageRules() throws Exception {
        byte[] png = new byte[5 * 1024 * 1024 + 10];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, png, 0, 8);
        mockMvc.perform(multipart(logoUrl()).file(new MockMultipartFile("file", "logo.png", "image/png", png)).with(pedagogy))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("5 Mo")));
    }
}
