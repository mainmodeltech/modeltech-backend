package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.modeltech.datamasteryhub.modules.training.enums.PayerType;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import lombok.Data;

import java.time.LocalDateTime;
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
    private String company;
    private String position;
    private String message;
    private RegistrationStatus status;

    // Acceptation et paiement
    private PayerType payerType;
    private Long totalAmount;
    private LocalDateTime acceptedAt;
    private String rejectedReason;
    private UUID learnerId;
    private String source;
    private String cancelledReason;
    /** Renseigné dans les listes et le détail admin dès que des échéances existent. */
    private PaymentSummaryResponse paymentSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
