package com.modeltech.datamasteryhub.modules.training.dto.request;


import com.modeltech.datamasteryhub.modules.training.enums.SessionFormat;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateBootcampSessionRequest {
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
    private String priceOverride;
    private String earlyBirdPrice;
    private LocalDate earlyBirdDeadline;
    private Boolean isFeatured;
    private Boolean published;
}
