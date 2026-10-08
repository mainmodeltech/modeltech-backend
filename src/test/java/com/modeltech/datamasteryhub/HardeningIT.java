package com.modeltech.datamasteryhub;

import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.cms.service.SiteSettingService;
import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Garde-fous de sécurité : anti-robot à l'inscription, limite de connexion, erreurs internes opaques. */
class HardeningIT extends AbstractIntegrationTest {

    private static final String REGISTRATION = """
            {"firstName":"Awa","lastName":"Diop","email":"awa@example.com","country":"Sénégal",
             "profile":"ENTREPRENEUR","recaptchaToken":"jeton"}""";

    @MockBean private RecaptchaService recaptchaService;
    @MockBean private IpRateLimiter rateLimiter;
    @MockBean private SiteSettingService siteSettingService;

    @Autowired private RegistrationRepository registrationRepository;

    @Test
    void registration_isRefusedWhenTheRecaptchaCheckFails() throws Exception {
        when(recaptchaService.verify("jeton")).thenReturn(false);

        mockMvc.perform(post("/api/v1/registrations").contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Vérification anti-robot échouée. Veuillez réessayer."));
        assertThat(registrationRepository.count()).isZero();
    }

    @Test
    void registration_goesThroughWhenTheRecaptchaCheckPasses() throws Exception {
        when(recaptchaService.verify("jeton")).thenReturn(true);

        mockMvc.perform(post("/api/v1/registrations").contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
                .andExpect(status().is2xxSuccessful());
        assertThat(registrationRepository.count()).isEqualTo(1);
    }

    @Test
    void login_isRateLimitedPerIp() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives. Veuillez réessayer dans une heure."))
                .when(rateLimiter).check(any(), eq(IpRateLimiter.LOGIN_SCOPE));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@model-technologie.com\",\"password\":\"mauvais\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void unexpectedErrors_neverLeakInternalDetails_butGiveAReference() throws Exception {
        when(siteSettingService.findAllPublic())
                .thenThrow(new IllegalStateException("could not execute statement: SELECT * FROM admin_users WHERE password_hash"));

        mockMvc.perform(get("/api/v1/site-settings"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Une erreur est survenue. Référence : ")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
    }
}
