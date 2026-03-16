package com.modeltech.datamasteryhub.modules.networking.mapper;

import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.*;
import com.modeltech.datamasteryhub.modules.networking.entity.Project;
import com.modeltech.datamasteryhub.modules.networking.entity.ProjectMember;
import com.modeltech.datamasteryhub.modules.networking.entity.ProjectScreenshot;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {AlumniMapper.class})
public interface ProjectMapper {

    // ── Create ────────────────────────────────────────────────────────────────

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "members",     ignore = true)   // géré manuellement dans le service
    @Mapping(target = "screenshots", ignore = true)
//    @Mapping(target = "createdAt",   ignore = true)
//    @Mapping(target = "updatedAt",   ignore = true)
//    @Mapping(target = "createdBy",   ignore = true)
//    @Mapping(target = "updatedBy",   ignore = true)
//    @Mapping(target = "deleted",     ignore = true)
//    @Mapping(target = "deletedAt",   ignore = true)
//    @Mapping(target = "deletedBy",   ignore = true)
    Project toEntity(CreateProjectRequest request);

    // ── Update (null-safe) ────────────────────────────────────────────────────

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "members",     ignore = true)
    @Mapping(target = "screenshots", ignore = true)
//    @Mapping(target = "createdAt",   ignore = true)
//    @Mapping(target = "updatedAt",   ignore = true)
//    @Mapping(target = "createdBy",   ignore = true)
//    @Mapping(target = "updatedBy",   ignore = true)
//    @Mapping(target = "deleted",     ignore = true)
//    @Mapping(target = "deletedAt",   ignore = true)
//    @Mapping(target = "deletedBy",   ignore = true)
    void updateEntityFromRequest(UpdateProjectRequest request, @MappingTarget Project entity);

    // ── Responses ─────────────────────────────────────────────────────────────

    ProjectResponse toResponse(Project entity);

    ProjectSummaryResponse toSummaryResponse(Project entity);

    // ── Sous-mappings ─────────────────────────────────────────────────────────

    @Mapping(target = "alumni", source = "alumni")
    ProjectMemberResponse toMemberResponse(ProjectMember member);

    ProjectScreenshotResponse toScreenshotResponse(ProjectScreenshot screenshot);
}
