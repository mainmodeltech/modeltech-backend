package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPartnerResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.PartnerResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PartnerMapper {

    /** Vue publique : sans part de revenu ni contact interne. */
    PartnerResponse toResponse(Partner partner);

    List<PartnerResponse> toResponseList(List<Partner> partners);

    AdminPartnerResponse toAdminResponse(Partner partner);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)            // validé et posé par le service (unicité)
    @Mapping(target = "logoUrl", ignore = true)         // renseigné par l'upload du logo
    @Mapping(target = "logoObjectKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    Partner toEntity(CreatePartnerRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    @Mapping(target = "logoObjectKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
//    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateEntity(UpdatePartnerRequest request, @MappingTarget Partner partner);
}
