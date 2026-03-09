package com.modeltech.datamasteryhub.modules.communication.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MasterclassRegistrationResponseDTO {
    private UUID id;
    private String masterclassId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String profile;
    private String company;
    private Boolean emailSent;
    private LocalDateTime createdAt;
}
