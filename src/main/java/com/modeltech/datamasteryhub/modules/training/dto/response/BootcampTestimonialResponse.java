package com.modeltech.datamasteryhub.modules.training.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BootcampTestimonialResponse {
    private String name;
    private String role;
    private String company;
    private String content;
    private String initials;
}
