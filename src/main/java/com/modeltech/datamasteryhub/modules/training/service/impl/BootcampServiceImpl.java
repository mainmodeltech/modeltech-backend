package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.common.util.SlugUtils;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.repository.TestimonialRepository;
import com.modeltech.datamasteryhub.modules.training.dto.request.*;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSessionResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampTestimonialResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.FormationSessionResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.mapper.BootcampMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.DomainRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BootcampServiceImpl implements BootcampService {

    /** Statuts de session affichés dans le calendrier public du catalogue. */
    private static final List<SessionStatus> CATALOGUE_SESSION_STATUSES =
            List.of(SessionStatus.OPEN, SessionStatus.UPCOMING);

    private final BootcampRepository bootcampRepository;
    private final BootcampSessionRepository sessionRepository;
    private final TestimonialRepository testimonialRepository;
    private final DomainRepository domainRepository;
    private final PartnerRepository partnerRepository;
    private final BootcampMapper mapper;
    private final com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository adminUserRepository;

    // ── Public ──────────────────────────────────────────────────────

    @Override
    public List<BootcampResponse> findAllPublished() {
        return bootcampRepository
                .findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(this::toResponseWithNextSession)
                .collect(Collectors.toList());
    }

    @Override
    public BootcampResponse findPublishedById(UUID id) {
        Bootcamp bootcamp = bootcampRepository.findByIdAndPublishedTrueAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp introuvable : " + id));
        return toResponseWithAllSessions(bootcamp, false);
    }

    // ── Public - catalogue par domaines ──────────────────────────────

    @Override
    public List<BootcampResponse> findCatalogue(CatalogueFilter filter) {
        CatalogueFilter f = filter != null ? filter : CatalogueFilter.none();
        return bootcampRepository.findAllForCatalogue().stream()
                .filter(b -> matchesCatalogueFilter(b, f))
                .map(this::toCatalogueResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BootcampResponse findCatalogueBySlug(String slug) {
        Bootcamp bootcamp = bootcampRepository.findForCatalogueBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Formation introuvable : " + slug));
        return toCatalogueResponse(bootcamp);
    }

    @Override
    public List<FormationSessionResponse> findCatalogueSessions() {
        return sessionRepository.findCatalogueSessions(CATALOGUE_SESSION_STATUSES).stream()
                .map(mapper::toFormationSessionResponse)
                .collect(Collectors.toList());
    }

    // ── Admin - Bootcamps ────────────────────────────────────────────

    @Override
    public List<BootcampResponse> findAllForAdmin() {
        return bootcampRepository
                .findAllByIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(this::toResponseWithNextSession)
                .collect(Collectors.toList());
    }

    @Override
    public BootcampResponse findByIdForAdmin(UUID id) {
        Bootcamp bootcamp = bootcampRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp introuvable : " + id));
        BootcampResponse response = toResponseWithAllSessions(bootcamp, true);
        response.setRelatedFormationIds(explicitRelatedIds(bootcamp));
        return response;
    }

    @Override
    @Transactional
    public BootcampResponse create(CreateBootcampRequest request) {
        Bootcamp bootcamp = mapper.toEntity(request);
        if (bootcamp.getDeliveredBy() == null) bootcamp.setDeliveredBy(DeliveredBy.INTERNAL);

        bootcamp.setSlug(resolveNewSlug(request.getSlug(), bootcamp.getTitle()));
        applyDomain(bootcamp, request.getDomainId());
        applyPartner(bootcamp, request.getPartnerId());
        applyRelated(bootcamp, request.getRelatedFormationIds());

        return toAdminResponse(bootcampRepository.save(bootcamp));
    }

    @Override
    @Transactional
    public BootcampResponse update(UUID id, UpdateBootcampRequest request) {
        Bootcamp bootcamp = getBootcampOrThrow(id);
        mapper.updateEntity(request, bootcamp);

        if (request.getSlug() != null && !request.getSlug().equals(bootcamp.getSlug())) {
            bootcamp.setSlug(resolveChangedSlug(request.getSlug(), id));
        }
        applyDomain(bootcamp, request.getDomainId());
        applyPartner(bootcamp, request.getPartnerId());
        applyRelated(bootcamp, request.getRelatedFormationIds());

        return toAdminResponse(bootcampRepository.save(bootcamp));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Bootcamp bootcamp = getBootcampOrThrow(id);
        bootcamp.setDeleted(true);
        bootcamp.setPublished(false);
        bootcampRepository.save(bootcamp);
    }

    @Override
    @Transactional
    public BootcampResponse togglePublished(UUID id) {
        Bootcamp bootcamp = getBootcampOrThrow(id);
        bootcamp.setPublished(!bootcamp.getPublished());
        return toAdminResponse(bootcampRepository.save(bootcamp));
    }

    // ── Admin - Sessions ─────────────────────────────────────────────

    @Override
    public List<BootcampSessionResponse> findSessionsByBootcamp(UUID bootcampId, boolean adminMode) {
        getBootcampOrThrow(bootcampId); // vérifie que le bootcamp existe
        List<BootcampSession> sessions = adminMode
                ? sessionRepository.findAllByBootcampIdAndIsDeletedFalseOrderByStartDateAsc(bootcampId)
                : sessionRepository.findAllByBootcampIdAndPublishedTrueAndIsDeletedFalseOrderByStartDateAsc(bootcampId);
        return mapper.toSessionResponseList(sessions);
    }

    @Override
    public BootcampSessionResponse findSessionById(UUID sessionId) {
        return mapper.toSessionResponse(getSessionOrThrow(sessionId));
    }

    @Override
    @Transactional
    public BootcampSessionResponse createSession(UUID bootcampId, CreateBootcampSessionRequest request) {
        Bootcamp bootcamp = getBootcampOrThrow(bootcampId);
        BootcampSession session = mapper.toSessionEntity(request);
        session.setBootcamp(bootcamp);
        return mapper.toSessionResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public BootcampSessionResponse updateSession(UUID sessionId, UpdateBootcampSessionRequest request) {
        BootcampSession session = getSessionOrThrow(sessionId);
        mapper.updateSessionEntity(request, session);
        // Recalcule isFull si currentParticipants ou max ont changé
        if (session.getMaxParticipants() != null && session.getCurrentParticipants() != null) {
            session.setIsFull(session.getCurrentParticipants() >= session.getMaxParticipants());
        }
        return mapper.toSessionResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public void deleteSession(UUID sessionId) {
        BootcampSession session = getSessionOrThrow(sessionId);
        session.setDeleted(true);
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public BootcampSessionResponse assignTrainer(UUID sessionId, UUID trainerId) {
        BootcampSession session = getSessionOrThrow(sessionId);
        if (trainerId == null) {
            session.setTrainer(null);
        } else {
            com.modeltech.datamasteryhub.modules.auth.entity.AdminUser trainer = adminUserRepository.findByIdAndIsDeletedFalse(trainerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Formateur", "id", trainerId));
            if (!trainer.isActive() || !trainer.hasRole(com.modeltech.datamasteryhub.modules.auth.entity.RoleNames.TRAINER)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Ce compte n'est pas un formateur actif (rôle TRAINER requis).");
            }
            session.setTrainer(trainer);
        }
        return mapper.toSessionResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public BootcampSessionResponse toggleSessionFeatured(UUID sessionId) {
        BootcampSession session = getSessionOrThrow(sessionId);
        session.setIsFeatured(!session.getIsFeatured());
        return mapper.toSessionResponse(sessionRepository.save(session));
    }

    // ── Privé ────────────────────────────────────────────────────────

    private Bootcamp getBootcampOrThrow(UUID id) {
        return bootcampRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp introuvable : " + id));
    }

    private BootcampSession getSessionOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Session introuvable : " + id));
    }

    /**
     * Response avec juste la prochaine session (pour les listings)
     */
    private BootcampResponse toResponseWithNextSession(Bootcamp bootcamp) {
        BootcampResponse response = mapper.toResponse(bootcamp);
        findNextOpenSession(bootcamp.getId()).ifPresent(response::setNextSession);
        response.setTestimonial(resolveTestimonial(bootcamp.getId()));
        return response;
    }

    /** Session mise en avant, sinon prochaine session OPEN/UPCOMING publiée. */
    private java.util.Optional<BootcampSessionResponse> findNextOpenSession(UUID bootcampId) {
        return sessionRepository.findFirstByBootcampIdAndIsFeaturedTrueAndPublishedTrueAndIsDeletedFalse(bootcampId)
                .or(() -> sessionRepository.findNextUpcomingSession(bootcampId))
                .map(mapper::toSessionResponse);
    }

    /**
     * Response avec toutes les sessions (pour la page détail)
     */
    private BootcampResponse toResponseWithAllSessions(Bootcamp bootcamp, boolean adminMode) {
        BootcampResponse response = mapper.toResponse(bootcamp);
        List<BootcampSession> sessions = adminMode
                ? sessionRepository.findAllByBootcampIdAndIsDeletedFalseOrderByStartDateAsc(bootcamp.getId())
                : sessionRepository.findAllByBootcampIdAndPublishedTrueAndIsDeletedFalseOrderByStartDateAsc(bootcamp.getId());
        response.setSessions(mapper.toSessionResponseList(sessions));
        // Prochaine session mise en avant
        sessions.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsFeatured()))
                .findFirst()
                .or(() -> sessions.stream().findFirst())
                .map(mapper::toSessionResponse)
                .ifPresent(response::setNextSession);
        response.setTestimonial(resolveTestimonial(bootcamp.getId()));
        return response;
    }

    /**
     * Témoignage à afficher sur la fiche formation (le plus prioritaire, publié,
     * lié à ce bootcamp). Absent si aucun témoignage n'est encore rattaché.
     */
    private BootcampTestimonialResponse resolveTestimonial(UUID bootcampId) {
        return testimonialRepository
                .findFirstByBootcampRefIdAndPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc(bootcampId)
                .map(t -> new BootcampTestimonialResponse(
                        t.getName(), t.getRole(), t.getCompany(), t.getContent(), computeInitials(t.getName())))
                .orElse(null);
    }

    private String computeInitials(String name) {
        if (name == null || name.isBlank()) return null;
        return Arrays.stream(name.trim().split("\\s+"))
                .filter(part -> !part.isEmpty())
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }

    // ── Catalogue : réponse « Formation » et filtres ─────────────────

    /**
     * Réponse complète d'une formation du catalogue : sessions, témoignage,
     * formations liées. Les listes sont toujours présentes (jamais omises) car le
     * contrat front Formation les déclare non nulles.
     */
    private BootcampResponse toCatalogueResponse(Bootcamp bootcamp) {
        BootcampResponse response = toResponseWithAllSessions(bootcamp, false);
        // Pour le CTA d'inscription : la session mise en avant, sinon la prochaine
        // session ouverte (et non « la première session », qui peut être terminée).
        response.setNextSession(findNextOpenSession(bootcamp.getId()).orElse(null));
        response.setRelatedFormationIds(resolveRelatedIds(bootcamp));
        ensureCollections(response);
        return response;
    }

    private void ensureCollections(BootcampResponse r) {
        if (r.getBenefits() == null) r.setBenefits(new ArrayList<>());
        if (r.getTargetRoles() == null) r.setTargetRoles(new ArrayList<>());
        if (r.getProfiles() == null) r.setProfiles(new ArrayList<>());
        if (r.getTools() == null) r.setTools(new ArrayList<>());
        if (r.getCurriculum() == null) r.setCurriculum(new ArrayList<>());
        if (r.getOutcomes() == null) r.setOutcomes(new ArrayList<>());
        if (r.getSessions() == null) r.setSessions(new ArrayList<>());
        if (r.getRelatedFormationIds() == null) r.setRelatedFormationIds(new ArrayList<>());
    }

    private boolean matchesCatalogueFilter(Bootcamp b, CatalogueFilter f) {
        if (hasText(f.domain()) && !f.domain().equalsIgnoreCase(b.getDomain().getSlug())) return false;
        if (f.deliveredBy() != null && b.getDeliveredBy() != f.deliveredBy()) return false;
        if (f.level() != null && b.getLevel() != f.level()) return false;
        if (f.format() != null && b.getFormat() != f.format()) return false;
        if (hasText(f.targetRole())
                && (b.getTargetRoles() == null
                || b.getTargetRoles().stream().noneMatch(r -> r.equalsIgnoreCase(f.targetRole())))) {
            return false;
        }
        return true;
    }

    /**
     * Formations liées : celles choisies à la main (publiées), sinon repli sur les
     * autres formations publiées du même domaine (3 maximum).
     */
    private List<UUID> resolveRelatedIds(Bootcamp bootcamp) {
        List<UUID> explicit = bootcamp.getRelatedFormations().stream()
                .filter(r -> !r.isDeleted() && Boolean.TRUE.equals(r.getPublished()))
                .map(Bootcamp::getId)
                .collect(Collectors.toList());
        if (!explicit.isEmpty() || bootcamp.getDomain() == null) return explicit;

        return bootcampRepository
                .findTop3ByDomainIdAndIdNotAndPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc(
                        bootcamp.getDomain().getId(), bootcamp.getId())
                .stream()
                .map(Bootcamp::getId)
                .collect(Collectors.toList());
    }

    /** Formations liées choisies à la main (vue admin : ce qui a été saisi, sans repli). */
    private List<UUID> explicitRelatedIds(Bootcamp bootcamp) {
        return bootcamp.getRelatedFormations().stream()
                .filter(r -> !r.isDeleted())
                .map(Bootcamp::getId)
                .collect(Collectors.toList());
    }

    private BootcampResponse toAdminResponse(Bootcamp bootcamp) {
        BootcampResponse response = mapper.toResponse(bootcamp);
        response.setRelatedFormationIds(explicitRelatedIds(bootcamp));
        return response;
    }

    // ── Admin : slug, domaine, partenaire, formations liées ──────────

    private String resolveNewSlug(String requested, String title) {
        if (hasText(requested)) {
            return validatedSlug(requested, bootcampRepository.existsBySlug(SlugUtils.slugify(requested)));
        }
        return SlugUtils.unique(title, "formation", bootcampRepository::existsBySlug);
    }

    private String resolveChangedSlug(String requested, UUID selfId) {
        String slug = SlugUtils.slugify(requested);
        return validatedSlug(requested, !slug.isEmpty() && bootcampRepository.existsBySlugAndIdNot(slug, selfId));
    }

    private String validatedSlug(String requested, boolean alreadyUsed) {
        String slug = SlugUtils.slugify(requested);
        if (slug.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le slug est invalide.");
        }
        if (alreadyUsed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce slug est déjà utilisé : " + slug);
        }
        return slug;
    }

    private void applyDomain(Bootcamp bootcamp, UUID domainId) {
        if (domainId == null) return;
        Domain domain = domainRepository.findByIdAndIsDeletedFalse(domainId)
                .orElseThrow(() -> new ResourceNotFoundException("Domaine", "id", domainId));
        bootcamp.setDomain(domain);
    }

    /**
     * INTERNAL : pas de partenaire (efface un éventuel partenaire). PARTNER : un
     * partenaire est obligatoire (celui fourni, ou celui déjà rattaché).
     */
    private void applyPartner(Bootcamp bootcamp, UUID partnerId) {
        if (bootcamp.getDeliveredBy() != DeliveredBy.PARTNER) {
            bootcamp.setPartner(null);
            return;
        }
        if (partnerId != null) {
            Partner partner = partnerRepository.findByIdAndIsDeletedFalse(partnerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Partenaire", "id", partnerId));
            bootcamp.setPartner(partner);
        }
        if (bootcamp.getPartner() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une formation dispensée par un partenaire doit avoir un partenaire (partnerId).");
        }
    }

    /** Remplace les formations liées si la liste est fournie (liste vide = aucune). */
    private void applyRelated(Bootcamp bootcamp, List<UUID> relatedIds) {
        if (relatedIds == null) return;
        Set<Bootcamp> related = new LinkedHashSet<>();
        for (UUID relatedId : relatedIds) {
            if (relatedId.equals(bootcamp.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Une formation ne peut pas être liée à elle-même.");
            }
            related.add(getBootcampOrThrow(relatedId));
        }
        bootcamp.getRelatedFormations().clear();
        bootcamp.getRelatedFormations().addAll(related);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}