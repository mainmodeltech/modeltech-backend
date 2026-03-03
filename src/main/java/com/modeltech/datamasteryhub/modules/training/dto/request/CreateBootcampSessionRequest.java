package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.enums.SessionFormat;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateBootcampSessionRequest {

    private String sessionName;

    private Integer cohortNumber;
    private Integer year;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate registrationDeadline;

    @Min(1)
    private Integer maxParticipants = 20;

    private SessionStatus status = SessionStatus.UPCOMING;
    private SessionFormat format = SessionFormat.PRESENTIEL;
    private String location;

    private String priceOverride;
    private String earlyBirdPrice;
    private LocalDate earlyBirdDeadline;

    private Boolean isFeatured = false;
    private Boolean published = true;
}
