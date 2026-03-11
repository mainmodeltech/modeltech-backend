package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Vérification du token reCAPTCHA v3 auprès de l'API Google.
 *
 * Seuil de score : 0.5 (0.0 = bot certain, 1.0 = humain certain)
 * Google recommande 0.5 comme valeur par défaut.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecaptchaServiceImpl implements RecaptchaService {
    private static final String VERIFY_URL =
            "https://www.google.com/recaptcha/api/siteverify";

    private static final double SCORE_THRESHOLD = 0.5;

    private final RestTemplate restTemplate;

    @Value("${app.recaptcha.secret-key}")
    private String secretKey;

    @Value("${app.recaptcha.enabled:true}")
    private boolean enabled;

    /**
     * Vérifie le token reCAPTCHA retourné par le frontend.
     *
     * @param token  Token généré par grecaptcha.execute() côté client
     * @return true si humain probable (score >= seuil), false sinon
     */
    public boolean verify(String token) {
        // Désactivable en dev/test via application.yml
        if (!enabled) {
            log.warn("reCAPTCHA désactivé — vérification ignorée");
            return true;
        }

        if (token == null || token.isBlank()) {
            log.warn("Token reCAPTCHA vide");
            return false;
        }

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", secretKey);
            params.add("response", token);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    VERIFY_URL, params, Map.class);

            if (response == null) {
                log.error("Réponse nulle de l'API reCAPTCHA");
                return false;
            }

            boolean success = Boolean.TRUE.equals(response.get("success"));
            double score = response.get("score") != null
                    ? ((Number) response.get("score")).doubleValue()
                    : 0.0;
            String action = (String) response.get("action");

            log.info("reCAPTCHA — success={}, score={}, action={}", success, score, action);

            if (!success) {
                log.warn("reCAPTCHA échoué — codes d'erreur : {}", response.get("error-codes"));
                return false;
            }

            if (score < SCORE_THRESHOLD) {
                log.warn("reCAPTCHA score trop bas : {} < {}", score, SCORE_THRESHOLD);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Erreur lors de la vérification reCAPTCHA : {}", e.getMessage(), e);
            // En cas d'erreur réseau vers Google, on laisse passer
            // pour ne pas bloquer les vrais utilisateurs
            return true;
        }
    }
}
