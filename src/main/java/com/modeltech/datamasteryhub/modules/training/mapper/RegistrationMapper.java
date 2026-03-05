package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RegistrationMapper {

    @Mapping(target = "bootcampId", source = "bootcamp.id")
    @Mapping(target = "sessionId", source = "session.id")
    RegistrationResponse toResponse(Registration entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bootcamp", ignore = true)
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "sessionName", ignore = true)
    @Mapping(target = "promoCodeId", ignore = true)
    @Mapping(target = "promoCodeUsed", ignore = true)
    @Mapping(target = "discountPercent", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    Registration toEntity(CreateRegistrationRequest request);
}
