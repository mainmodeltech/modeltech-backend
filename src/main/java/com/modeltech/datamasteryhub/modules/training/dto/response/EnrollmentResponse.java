package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.modeltech.datamasteryhub.modules.training.enums.EnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EnrollmentResponse {
    private UUID id;
    private UUID learnerId;
    private String learnerEmail;
    private String learnerName;
    private UUID registrationId;
    private UUID sessionId;
    private String sessionName;
    private String bootcampTitle;
    private EnrollmentStatus status;
    private LocalDate accessStartsAt;
    private LocalDate accessEndsAt;
    private LocalDateTime createdAt;
}
