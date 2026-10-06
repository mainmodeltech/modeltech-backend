package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminDomainResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.DomainResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DomainMapper {

    DomainResponse toResponse(Domain domain);

    List<DomainResponse> toResponseList(List<Domain> domains);

    AdminDomainResponse toAdminResponse(Domain domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)   // validé et posé par le service (unicité)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    Domain toEntity(CreateDomainRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateEntity(UpdateDomainRequest request, @MappingTarget Domain domain);
}
