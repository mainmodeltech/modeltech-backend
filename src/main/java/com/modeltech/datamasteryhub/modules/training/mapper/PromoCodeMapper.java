package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePromoCodeRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PromoCodeResponse;
import com.modeltech.datamasteryhub.modules.training.entity.PromoCode;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface PromoCodeMapper {

    @Mapping(target = "remainingUses", expression = "java(calcRemainingUses(entity))")
    @Mapping(target = "expired", expression = "java(isExpired(entity))")
    PromoCodeResponse toResponse(PromoCode entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usageCount", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    PromoCode toEntity(CreatePromoCodeRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usageCount", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateEntity(UpdatePromoCodeRequest request, @MappingTarget PromoCode entity);

    default Integer calcRemainingUses(PromoCode entity) {
        if (entity.getMaxUses() == null) return null;
        return Math.max(0, entity.getMaxUses() - entity.getUsageCount());
    }

    default Boolean isExpired(PromoCode entity) {
        if (entity.getExpiresAt() == null) return false;
        return entity.getExpiresAt().isBefore(LocalDateTime.now());
    }
}
