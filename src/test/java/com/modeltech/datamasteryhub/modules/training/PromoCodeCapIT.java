package com.modeltech.datamasteryhub.modules.training;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.training.entity.PromoCode;
import com.modeltech.datamasteryhub.modules.training.repository.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un code de parrainage ne dépasse jamais 30 % ; un code plus ancien au-dessus du plafond est signalé et refusé aux candidats. */
class PromoCodeCapIT extends AbstractIntegrationTest {

    private static final String ADMIN = "/api/v1/admin/promo-codes";
    private final RequestPostProcessor finance = TestAuth.as("fin@test.local", "FINANCE");

    @Autowired private PromoCodeRepository promoCodeRepository;

    @Test
    void creationAbove30IsRefused_30IsAccepted() throws Exception {
        mockMvc.perform(post(ADMIN).with(finance).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GROS\",\"referrerName\":\"Awa\",\"discountPercent\":31}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN).with(finance).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MAX30\",\"referrerName\":\"Awa\",\"discountPercent\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overLimit").value(false))
                .andExpect(jsonPath("$.maxDiscountPercent").value(30));
    }

    @Test
    void legacyCodeAboveTheCap_isFlagged_refusedToCandidates_andMustBeCorrected() throws Exception {
        PromoCode legacy = new PromoCode();
        legacy.setCode("ANCIEN50");
        legacy.setReferrerName("Moussa");
        legacy.setDiscountPercent(50);
        legacy.setIsActive(true);
        legacy.setUsageCount(0);
        legacy = promoCodeRepository.save(legacy);

        // Signalé à l'administration
        mockMvc.perform(get(ADMIN + "/" + legacy.getId()).with(finance))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overLimit").value(true));

        // Refusé au candidat : le code est traité comme invalide
        mockMvc.perform(get("/api/v1/promo-codes/validate").param("code", "ANCIEN50"))
                .andExpect(status().isNotFound());

        // Corrigé au plafond ou en dessous : le code redevient utilisable
        mockMvc.perform(put(ADMIN + "/" + legacy.getId()).with(finance).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountPercent\":45}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(ADMIN + "/" + legacy.getId()).with(finance).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountPercent\":30}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.overLimit").value(false));
        mockMvc.perform(get("/api/v1/promo-codes/validate").param("code", "ANCIEN50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.discountPercent").value(30));
    }
}
