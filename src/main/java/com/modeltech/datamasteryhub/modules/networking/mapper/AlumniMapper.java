package com.modeltech.datamasteryhub.modules.networking.mapper;

import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniSummaryResponse;
import com.modeltech.datamasteryhub.modules.networking.entity.Alumni;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AlumniMapper {

    // ── Create ────────────────────────────────────────────────────────────────

    @Mapping(target = "id",                   ignore = true)
    @Mapping(target = "registration",         ignore = true)   // résolu dans le service
    @Mapping(target = "projectMemberships",   ignore = true)
//    @Mapping(target = "createdAt",            ignore = true)
//    @Mapping(target = "updatedAt",            ignore = true)
//    @Mapping(target = "createdBy",            ignore = true)
//    @Mapping(target = "updatedBy",            ignore = true)
//    @Mapping(target = "deleted",              ignore = true)
//    @Mapping(target = "deletedAt",            ignore = true)
//    @Mapping(target = "deletedBy",            ignore = true)
    Alumni toEntity(CreateAlumniRequest request);

    // ── Update (null-safe) ────────────────────────────────────────────────────

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id",                   ignore = true)
    @Mapping(target = "registration",         ignore = true)
    @Mapping(target = "projectMemberships",   ignore = true)
//    @Mapping(target = "createdAt",            ignore = true)
//    @Mapping(target = "updatedAt",            ignore = true)
//    @Mapping(target = "createdBy",            ignore = true)
//    @Mapping(target = "updatedBy",            ignore = true)
//    @Mapping(target = "deleted",              ignore = true)
//    @Mapping(target = "deletedAt",            ignore = true)
//    @Mapping(target = "deletedBy",            ignore = true)
    void updateEntityFromRequest(UpdateAlumniRequest request, @MappingTarget Alumni entity);

    // ── Responses ─────────────────────────────────────────────────────────────

    @Mapping(target = "registrationId", source = "registration.id")
    AlumniResponse toResponse(Alumni entity);

    AlumniSummaryResponse toSummaryResponse(Alumni entity);
}
