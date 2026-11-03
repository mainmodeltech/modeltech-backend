package com.modeltech.datamasteryhub.modules.legal.service;

import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.legal.LegalCode;
import com.modeltech.datamasteryhub.modules.legal.LegalContext;
import com.modeltech.datamasteryhub.modules.legal.LegalStatus;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.AcceptanceView;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.AdminDocument;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.AdminOverview;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.DocumentRef;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.Publish;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.PublicDocument;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.Requirements;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.SaveDraft;
import com.modeltech.datamasteryhub.modules.legal.entity.LegalAcceptance;
import com.modeltech.datamasteryhub.modules.legal.entity.LegalDocument;
import com.modeltech.datamasteryhub.modules.legal.repository.LegalAcceptanceRepository;
import com.modeltech.datamasteryhub.modules.legal.repository.LegalDocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Textes légaux versionnés et preuves d'acceptation.
 *
 * <p><b>Principe :</b> seuls les textes <em>publiés</em> sont exigés. Un texte n'est publié que par la Direction, après validation du conseil
 * juridique (qui, quand) et une fois tous les champs « [À COMPLÉTER] » remplis ; la version publiée est ensuite figée. Chaque acceptation
 * enregistre la version exacte lue (et l'empreinte de son contenu), l'adresse e-mail, l'acte concerné, l'heure, l'adresse IP et le navigateur.
 * L'exigence est contrôlée <b>côté serveur</b> : le navigateur ne peut pas la contourner.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalService {

    /** Marque laissée dans les modèles de texte pour tout ce que l'entreprise doit renseigner : elle bloque la publication. */
    public static final String PLACEHOLDER = "[À COMPLÉTER";

    private final LegalDocumentRepository documents;
    private final LegalAcceptanceRepository acceptances;
    private final AuditService audit;

    // ── Public ──────────────────────────────────────────────────────

    public List<DocumentRef> published() {
        return documents.findByStatus(LegalStatus.PUBLISHED).stream().map(LegalService::ref)
                .sorted((a, b) -> a.code().compareTo(b.code())).toList();
    }

    public PublicDocument publishedBody(LegalCode code) {
        LegalDocument d = documents.findByCodeAndStatus(code, LegalStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Texte légal publié", "code", code));
        return new PublicDocument(ref(d), d.getBody());
    }

    /** Les textes publiés à accepter pour un acte. */
    public Requirements requirements(LegalContext context) {
        return new Requirements(context, requiredDocs(context).stream().map(LegalService::ref).toList());
    }

    private List<LegalDocument> requiredDocs(LegalContext context) {
        return context.codes().stream().map(c -> documents.findByCodeAndStatus(c, LegalStatus.PUBLISHED)).flatMap(java.util.Optional::stream).toList();
    }

    // ── Exigence et preuve ──────────────────────────────────────────

    /** Refuse (409) tant que les textes publiés exigés pour cet acte ne sont pas tous acceptés. Rien d'exigé = rien ne bloque. */
    public void requireAccepted(LegalContext context, List<UUID> acceptedIds) {
        List<LegalDocument> required = requiredDocs(context);
        if (required.isEmpty()) return;
        var given = acceptedIds == null ? java.util.Set.<UUID>of() : new java.util.HashSet<>(acceptedIds);
        boolean ok = required.stream().allMatch(d -> given.contains(d.getId()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous devez accepter les conditions en vigueur pour continuer (elles ont peut-être été mises à jour : rechargez la page).");
        }
    }

    /** Enregistre l'acceptation des textes exigés pour cet acte (ceux de la liste fournie qui sont bien en vigueur). */
    @Transactional
    public List<LegalAcceptance> record(LegalContext context, String subjectType, String subjectRef, String email, String fullName, List<UUID> acceptedIds) {
        List<LegalDocument> required = requiredDocs(context);
        if (required.isEmpty()) return List.of();
        var given = acceptedIds == null ? java.util.Set.<UUID>of() : new java.util.HashSet<>(acceptedIds);
        String ip = null;
        String userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ip = IpRateLimiter.clientIp(request);
            String ua = request.getHeader("User-Agent");
            userAgent = ua == null ? null : ua.substring(0, Math.min(255, ua.length()));
        }
        List<UUID> already = acceptances.acceptedDocumentIds(subjectType, subjectRef);
        List<LegalAcceptance> saved = new java.util.ArrayList<>();
        for (LegalDocument d : required) {
            if (!given.contains(d.getId()) || already.contains(d.getId())) continue;
            LegalAcceptance a = new LegalAcceptance();
            a.setDocument(d);
            a.setDocumentCode(d.getCode().name());
            a.setDocumentVersion(d.getVersion());
            a.setContentHash(d.getContentHash());
            a.setContext(context.name());
            a.setSubjectType(subjectType);
            a.setSubjectRef(subjectRef);
            a.setEmail(email.trim().toLowerCase(Locale.ROOT));
            a.setFullName(fullName);
            a.setIpAddress(ip);
            a.setUserAgent(userAgent);
            saved.add(acceptances.save(a));
        }
        return saved;
    }

    /** Vrai si tous les textes publiés exigés pour cet acte ont déjà été acceptés pour ce sujet précis. */
    public boolean hasAccepted(LegalContext context, String subjectType, String subjectRef) {
        List<LegalDocument> required = requiredDocs(context);
        if (required.isEmpty()) return true;
        List<UUID> accepted = acceptances.acceptedDocumentIds(subjectType, subjectRef);
        return required.stream().allMatch(d -> accepted.contains(d.getId()));
    }

    /** Textes exigés pour cet acte qui n'ont pas encore été acceptés pour ce sujet précis. */
    public List<DocumentRef> pendingForSubject(LegalContext context, String subjectType, String subjectRef) {
        List<LegalDocument> required = requiredDocs(context);
        if (required.isEmpty()) return List.of();
        List<UUID> accepted = acceptances.acceptedDocumentIds(subjectType, subjectRef);
        return required.stream().filter(d -> !accepted.contains(d.getId())).map(LegalService::ref).toList();
    }

    /** Textes exigés que cette personne n'a pas encore acceptés (dans leur version en vigueur), quel que soit l'acte d'origine. */
    public List<DocumentRef> pendingFor(LegalContext context, String email) {
        List<LegalDocument> required = requiredDocs(context);
        if (required.isEmpty()) return List.of();
        List<UUID> accepted = acceptances.acceptedByEmail(email, required.stream().map(LegalDocument::getId).toList());
        return required.stream().filter(d -> !accepted.contains(d.getId())).map(LegalService::ref).toList();
    }

    // ── Administration ──────────────────────────────────────────────

    public List<AdminOverview> overview() {
        return java.util.Arrays.stream(LegalCode.values()).map(code -> {
            List<LegalDocument> all = documents.findByCodeOrderByVersionDesc(code);
            AdminDocument published = all.stream().filter(d -> d.getStatus() == LegalStatus.PUBLISHED).findFirst().map(LegalService::admin).orElse(null);
            AdminDocument draft = all.stream().filter(d -> d.getStatus() == LegalStatus.DRAFT).findFirst().map(LegalService::admin).orElse(null);
            List<AdminDocument> history = all.stream().filter(d -> d.getStatus() != LegalStatus.DRAFT).map(LegalService::admin).toList();
            return new AdminOverview(code, code.title(), "/" + code.slug(), published, draft, history);
        }).toList();
    }

    /** Crée ou met à jour le brouillon du texte (une nouvelle version démarre à partir de la version en vigueur). */
    @Transactional
    public AdminDocument saveDraft(LegalCode code, SaveDraft req, String actor) {
        LegalDocument draft = documents.findByCodeAndStatus(code, LegalStatus.DRAFT).orElse(null);
        if (draft == null) {
            draft = new LegalDocument();
            draft.setCode(code);
            draft.setVersion(documents.maxVersion(code) + 1);
            draft.setStatus(LegalStatus.DRAFT);
            draft.setCreatedBy(actor);
        }
        draft.setTitle(req.title().trim());
        draft.setBody(req.body());
        draft.setChangeSummary(req.changeSummary() == null || req.changeSummary().isBlank() ? null : req.changeSummary().trim());
        return admin(documents.save(draft));
    }

    /** Abandonne le brouillon en cours (la version en vigueur n'est jamais touchée). */
    @Transactional
    public void discardDraft(LegalCode code) {
        LegalDocument draft = documents.findByCodeAndStatus(code, LegalStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Brouillon", "code", code));
        documents.delete(draft);
    }

    /**
     * Publie le brouillon : il devient la version en vigueur (figée) et remplace la précédente. Exige la validation du conseil juridique et que tous
     * les champs « [À COMPLÉTER] » soient renseignés.
     */
    @Transactional
    public AdminDocument publish(LegalCode code, Publish req, String actor) {
        LegalDocument draft = documents.lockDraft(code).orElseThrow(() -> new ResourceNotFoundException("Brouillon", "code", code));
        if (hasPlaceholders(draft.getBody()) || hasPlaceholders(draft.getTitle())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le texte contient encore des champs « " + PLACEHOLDER + " » : renseignez-les avant de publier.");
        }
        documents.findByCodeAndStatus(code, LegalStatus.PUBLISHED).ifPresent(old -> {
            old.setStatus(LegalStatus.SUPERSEDED);
            documents.saveAndFlush(old);   // libère « une seule version en vigueur » avant d'en publier une autre
        });
        draft.setStatus(LegalStatus.PUBLISHED);
        draft.setContentHash(sha256(draft.getBody()));
        draft.setCounselValidatedBy(req.counselValidatedBy().trim());
        draft.setCounselValidatedOn(req.counselValidatedOn());
        draft.setChangeSummary(req.changeSummary().trim());
        draft.setPublishedAt(LocalDateTime.now());
        draft.setPublishedBy(actor);
        documents.save(draft);
        audit.record(AuditAction.LEGAL_PUBLISHED, "LegalDocument", draft.getId(),
                code.title() + " : version " + draft.getVersion() + " publiée par " + actor + " (validée par " + draft.getCounselValidatedBy()
                        + " le " + draft.getCounselValidatedOn() + ") : " + draft.getChangeSummary());
        return admin(draft);
    }

    public Page<AcceptanceView> acceptances(String email, LegalCode code, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        return acceptances.search(email == null || email.isBlank() ? null : email.trim(), code == null ? null : code.name(), pageable)
                .map(a -> new AcceptanceView(a.getId(), LegalCode.valueOf(a.getDocumentCode()), a.getDocumentVersion(), a.getContentHash(), a.getContext(),
                        a.getSubjectType(), a.getSubjectRef(), a.getEmail(), a.getFullName(), a.getAcceptedAt(), a.getIpAddress()));
    }

    // ── Aides ───────────────────────────────────────────────────────

    public static boolean hasPlaceholders(String text) {
        return text != null && text.contains(PLACEHOLDER);
    }

    private static DocumentRef ref(LegalDocument d) {
        return new DocumentRef(d.getId(), d.getCode(), d.getTitle(), d.getVersion(), d.getPublishedAt(), "/" + d.getCode().slug());
    }

    private static AdminDocument admin(LegalDocument d) {
        return new AdminDocument(d.getId(), d.getCode(), d.getTitle(), d.getVersion(), d.getStatus(), d.getBody(), d.getChangeSummary(),
                d.getCounselValidatedBy(), d.getCounselValidatedOn(), d.getPublishedAt(), d.getPublishedBy(), d.getCreatedAt(), d.getUpdatedAt(),
                hasPlaceholders(d.getBody()));
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
