package com.modeltech.datamasteryhub.modules.legal.service;

import com.modeltech.datamasteryhub.modules.legal.LegalCode;
import com.modeltech.datamasteryhub.modules.legal.LegalStatus;
import com.modeltech.datamasteryhub.modules.legal.entity.LegalDocument;
import com.modeltech.datamasteryhub.modules.legal.repository.LegalDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Au démarrage, charge pour chaque texte légal absent un <b>brouillon</b> (version 1) à partir des modèles du dépôt. Un modèle n'est jamais publié
 * de lui-même : il contient des champs « [À COMPLÉTER] » que l'entreprise et son conseil juridique doivent renseigner, ce qui bloque la publication
 * tant qu'ils y sont. Un texte déjà présent en base (brouillon, publié ou ancien) n'est jamais écrasé.
 * Désactivable avec {@code app.legal.seed-templates=false} (tests).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegalTemplates implements ApplicationRunner {

    private static final Map<LegalCode, String> FILES = Map.of(
            LegalCode.CGU, "legal/cgu.md",
            LegalCode.CGV, "legal/cgv.md",
            LegalCode.PRIVACY, "legal/privacy.md",
            LegalCode.MENTIONS, "legal/mentions.md");

    private final LegalDocumentRepository documents;

    @Value("${app.legal.seed-templates:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) seedMissing();
    }

    @Transactional
    public int seedMissing() {
        int created = 0;
        for (LegalCode code : LegalCode.values()) {
            if (documents.existsByCode(code)) continue;
            try {
                String body = new String(new ClassPathResource(FILES.get(code)).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                LegalDocument d = new LegalDocument();
                d.setCode(code);
                d.setVersion(1);
                d.setTitle(code.title());
                d.setBody(body);
                d.setStatus(LegalStatus.DRAFT);
                d.setChangeSummary("Modèle initial à compléter et à faire valider par le conseil juridique.");
                d.setCreatedBy("système");
                documents.save(d);
                created++;
            } catch (IOException e) {
                log.error("Modèle de texte légal {} introuvable : {}", code, e.getMessage());
            }
        }
        if (created > 0) log.info("{} modèle(s) de texte légal chargé(s) en brouillon", created);
        return created;
    }
}
