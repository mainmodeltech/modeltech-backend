package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private UUID registrationId;
    private Integer amount;
    private LocalDate paymentDate;
    private PaymentMethod paymentMethod;
    private String reference;
    private String notes;
    private String recordedBy;
    private LocalDateTime createdAt;
}
