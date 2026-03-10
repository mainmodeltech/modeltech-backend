package com.modeltech.datamasteryhub.modules.communication.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TestimonialResponseDTO {

    private UUID id;
    private String name;
    private String content;
    private String company;
    private String role;
    private String bootcamp;
    private String result;
    private Integer rating;
    private Boolean published;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
