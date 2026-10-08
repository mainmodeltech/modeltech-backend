// ============================================================
// FICHIER : mapper/BootcampMapper.java
// ============================================================
package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampSessionRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampSessionRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSessionResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.FormationSessionResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {DomainMapper.class, PartnerMapper.class}
)
public interface BootcampMapper {

    // ── Entity → Response ────────────────────────────────────────

    @Mapping(target = "nextSession", ignore = true)  // calculé dans le service
    @Mapping(target = "sessions", ignore = true)      // chargé selon le contexte
    @Mapping(target = "testimonial", ignore = true)   // résolu dans le service via TestimonialRepository
    @Mapping(target = "domainId", source = "domain.id")
    @Mapping(target = "partnerId", source = "partner.id")
    @Mapping(target = "relatedFormationIds", ignore = true) // calculé dans le service (liées + repli même domaine)
    BootcampResponse toResponse(Bootcamp bootcamp);

    List<BootcampResponse> toResponseList(List<Bootcamp> bootcamps);

    @Named("session")
    @Mapping(target = "bootcampId", source = "bootcamp.id")
    @Mapping(target = "price", expression = "java(resolvePrice(session))")
    @Mapping(target = "spotsRemaining", expression = "java(computeSpots(session))")
    @Mapping(target = "trainerId", source = "trainer.id")
    @Mapping(target = "trainerName", source = "trainer.fullName")
    BootcampSessionResponse toSessionResponse(BootcampSession session);

    @IterableMapping(qualifiedByName = "session")
    List<BootcampSessionResponse> toSessionResponseList(List<BootcampSession> sessions);

    /** Session « à plat » enrichie de sa formation (vue calendrier du catalogue). */
    @Mapping(target = "bootcampId", source = "bootcamp.id")
    @Mapping(target = "price", expression = "java(resolvePrice(session))")
    @Mapping(target = "spotsRemaining", expression = "java(computeSpots(session))")
    @Mapping(target = "formationId", source = "bootcamp.id")
    @Mapping(target = "formationSlug", source = "bootcamp.slug")
    @Mapping(target = "formationTitle", source = "bootcamp.title")
    @Mapping(target = "domainName", source = "bootcamp.domain.name")
    @Mapping(target = "deliveredBy", source = "bootcamp.deliveredBy")
    @Mapping(target = "partnerName", source = "bootcamp.partner.name")
    FormationSessionResponse toFormationSessionResponse(BootcampSession session);

    // ── Request → Entity ─────────────────────────────────────────

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sessions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "nextSession", ignore = true)
    @Mapping(target = "slug", ignore = true)              // posé par le service (génération + unicité)
    @Mapping(target = "domain", ignore = true)            // résolus par le service (domainId, partnerId…)
    @Mapping(target = "partner", ignore = true)
    @Mapping(target = "relatedFormations", ignore = true)
    Bootcamp toEntity(CreateBootcampRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bootcamp", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "isFull", ignore = true)
    @Mapping(target = "currentParticipants", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    BootcampSession toSessionEntity(CreateBootcampSessionRequest request);

    // ── Partial update (PATCH semantics) ─────────────────────────

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sessions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "nextSession", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "domain", ignore = true)
    @Mapping(target = "partner", ignore = true)
    @Mapping(target = "relatedFormations", ignore = true)
    void updateEntity(UpdateBootcampRequest request, @MappingTarget Bootcamp bootcamp);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bootcamp", ignore = true)
    @Mapping(target = "trainer", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateSessionEntity(UpdateBootcampSessionRequest request, @MappingTarget BootcampSession session);

    // ── Helpers ──────────────────────────────────────────────────

    default String resolvePrice(BootcampSession session) {
        if (session.getPriceOverride() != null && !session.getPriceOverride().isBlank()) {
            return session.getPriceOverride();
        }
        return session.getBootcamp() != null ? session.getBootcamp().getPrice() : null;
    }

    default Integer computeSpots(BootcampSession session) {
        if (session.getMaxParticipants() == null) return null;
        int current = session.getCurrentParticipants() != null ? session.getCurrentParticipants() : 0;
        return Math.max(0, session.getMaxParticipants() - current);
    }
}
