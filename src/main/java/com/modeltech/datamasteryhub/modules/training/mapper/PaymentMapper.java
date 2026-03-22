package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PaymentResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "registrationId", source = "registration.id")
    PaymentResponse toResponse(Payment entity);

    List<PaymentResponse> toResponseList(List<Payment> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registration", ignore = true)
    @Mapping(target = "recordedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    Payment toEntity(CreatePaymentRequest request);
}
