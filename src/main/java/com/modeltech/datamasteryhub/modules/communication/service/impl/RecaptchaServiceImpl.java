package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Vérification du token reCAPTCHA v3 auprès de l'API Google.
 *
 * Seuil de score : 0.5 (0.0 = bot certain, 1.0 = humain certain)
 * Google recommande 0.5 comme valeur par défaut.
 *
 * <p>Politique « échec = refus » : si Google ne répond pas (réseau, délai dépassé, réponse illisible),
 * la vérification échoue. Laisser passer dans ce cas aurait permis à un robot de contourner le contrôle
 * dès qu'il parvient à ralentir ou perturber l'appel ; les formulaires restent protégés par la limitation
 * de débit par IP, et un visiteur légitime peut simplement réessayer.
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

    /** Noms d'hôte autorisés (domaines du site, séparés par des virgules) ; vide = pas de contrôle. */
    @Value("${app.recaptcha.allowed-hostnames:}")
    private List<String> allowedHostnames;

    /**
     * Vérifie le token reCAPTCHA retourné par le frontend.
     *
     * @param token  Token généré par grecaptcha.execute() côté client
     * @return true si humain probable (score >= seuil), false sinon
     */
    @Override
    public boolean verify(String token) {
        return verify(token, null);
    }

    @Override
    public boolean verify(String token, String expectedAction) {
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
            double score = response.get("score") instanceof Number n ? n.doubleValue() : 0.0;
            Object action = response.get("action");
            Object hostname = response.get("hostname");

            log.info("reCAPTCHA — success={}, score={}, action={}", success, score, action);

            if (!success) {
                log.warn("reCAPTCHA échoué — codes d'erreur : {}", response.get("error-codes"));
                return false;
            }

            if (score < SCORE_THRESHOLD) {
                log.warn("reCAPTCHA score trop bas : {} < {}", score, SCORE_THRESHOLD);
                return false;
            }

            if (expectedAction != null && !expectedAction.equals(action)) {
                log.warn("reCAPTCHA : action « {} » reçue, « {} » attendue", action, expectedAction);
                return false;
            }

            if (hasAllowedHostnames() && !allowedHostnames.contains(String.valueOf(hostname))) {
                log.warn("reCAPTCHA : nom d'hôte « {} » non autorisé", hostname);
                return false;
            }

            return true;

        } catch (Exception e) {
            // Échec = refus (voir l'en-tête de la classe)
            log.error("Erreur lors de la vérification reCAPTCHA : {}", e.getMessage());
            return false;
        }
    }

    private boolean hasAllowedHostnames() {
        return allowedHostnames != null && allowedHostnames.stream().anyMatch(h -> !h.isBlank());
    }
}
