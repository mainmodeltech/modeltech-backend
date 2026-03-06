package com.modeltech.datamasteryhub.modules.communication.dto.response;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TestimonialResponseDTO {
    private UUID id;
    private String name;
    private String role;
    private String content;
    private String rating;
    private String company;
    private Boolean published;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

}
