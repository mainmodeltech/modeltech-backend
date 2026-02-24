package com.modeltech.datamasteryhub.modules.cms.mapper;

import com.modeltech.datamasteryhub.modules.cms.dto.request.CreateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.request.UpdateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceResponse;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceSummaryResponse;
import com.modeltech.datamasteryhub.modules.cms.entity.Service;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ServiceMapper {

    ServiceResponse toResponse(Service entity);

    ServiceSummaryResponse toSummaryResponse(Service entity);

    Service toEntity(CreateServiceRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(UpdateServiceRequest request, @MappingTarget Service entity);
}
