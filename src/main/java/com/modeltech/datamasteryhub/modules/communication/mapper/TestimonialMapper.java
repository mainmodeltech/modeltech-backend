package com.modeltech.datamasteryhub.modules.communication.mapper;

import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.Testimonial;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TestimonialMapper {
    Testimonial toEntity(TestimonialRequestDTO dto);
    TestimonialResponseDTO toResponseDto(Testimonial entity);
}
