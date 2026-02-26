package com.modeltech.datamasteryhub.modules.communication.dto.response;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ContactMessageResponseDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String company;
    private String subject;
    private String message;
    private ContactMessageStatus status;
    private String notes;
    private LocalDateTime createdAt;
}
