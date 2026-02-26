package com.modeltech.datamasteryhub.modules.communication.mapper;

import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ContactMessageMapper {
    ContactMessage toEntity(ContactMessageRequestDTO dto);
    ContactMessageResponseDTO toResponseDto(ContactMessage entity);
}
