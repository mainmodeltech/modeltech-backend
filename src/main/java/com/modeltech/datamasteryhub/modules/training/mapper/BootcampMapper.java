package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSummaryResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface BootcampMapper {

    BootcampResponse toResponse(Bootcamp entity);

    BootcampSummaryResponse toSummaryResponse(Bootcamp entity);

    Bootcamp toEntity(CreateBootcampRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(UpdateBootcampRequest request, @MappingTarget Bootcamp entity);
}
