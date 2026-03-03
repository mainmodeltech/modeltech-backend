package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.modeltech.datamasteryhub.modules.training.enums.SessionFormat;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BootcampSessionResponse {
    private UUID id;
    private UUID bootcampId;
    private String sessionName;
    private Integer cohortNumber;
    private Integer year;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate registrationDeadline;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Boolean isFull;
    private SessionStatus status;
    private SessionFormat format;
    private String location;
    private String price;           // priceOverride si défini, sinon prix du bootcamp
    private String earlyBirdPrice;
    private LocalDate earlyBirdDeadline;
    private Boolean isFeatured;
    private Boolean published;
    private Integer spotsRemaining;  // calculé : maxParticipants - currentParticipants
}
