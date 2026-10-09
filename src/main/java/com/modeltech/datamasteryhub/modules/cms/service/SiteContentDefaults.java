package com.modeltech.datamasteryhub.modules.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.modules.cms.repository.SiteSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Contenus par défaut du site (classpath : {@code site-content/<clé>.json}).
 * <ul>
 *   <li>au démarrage, chaque clé ABSENTE de la base est créée avec sa valeur par défaut : le site fonctionne
 *       dès le premier lancement, et une valeur déjà enregistrée (même supprimée) n'est jamais écrasée ;</li>
 *   <li>l'administration peut « restaurer » une clé : sa valeur redevient celle du fichier.</li>
 * </ul>
 * Désactivable avec {@code app.site-content.seed-defaults=false} (tests).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SiteContentDefaults {

    private static final String PATTERN = "classpath*:site-content/*.json";

    private final SiteSettingRepository repository;
    private final SiteSettingService service;
    private final ObjectMapper objectMapper;

    @Value("${app.site-content.seed-defaults:true}")
    private boolean seedOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (seedOnStartup) {
            log.info("Contenus du site : {} clé(s) par défaut ajoutée(s)", seedMissing());
        }
    }

    /** Crée les clés qui n'existent pas encore ; renvoie leur nombre. */
    public int seedMissing() {
        int created = 0;
        for (Map.Entry<String, JsonNode> entry : all().entrySet()) {
            if (repository.findByKey(entry.getKey()).isEmpty()) {
                service.upsert(entry.getKey(), entry.getValue());
                created++;
            }
        }
        return created;
    }

    /** Toutes les valeurs par défaut, par clé (ordre alphabétique). */
    public Map<String, JsonNode> all() {
        Map<String, JsonNode> result = new TreeMap<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                try (InputStream in = resource.getInputStream()) {
                    result.put(filename.substring(0, filename.length() - ".json".length()), objectMapper.readTree(in));
                }
            }
        } catch (IOException e) {
            log.error("Lecture des contenus par défaut impossible : {}", e.getMessage());
        }
        return result;
    }

    public Optional<JsonNode> defaultFor(String key) {
        return Optional.ofNullable(all().get(key));
    }
}
