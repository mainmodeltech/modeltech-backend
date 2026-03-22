package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class RegistrationResponse {

    private UUID id;
    private UUID bootcampId;
    private String bootcampTitle;
    private UUID sessionId;
    private String sessionName;
    private String promoCodeUsed;
    private Integer discountPercent;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String country;
    private String profile;
    private String school;
    private String company;
    private String position;
    private String message;
    private RegistrationStatus status;

    // Paiement
    private PaymentStatus paymentStatus;
    private Integer amountDue;
    private Integer amountPaid;
    private List<PaymentResponse> payments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
