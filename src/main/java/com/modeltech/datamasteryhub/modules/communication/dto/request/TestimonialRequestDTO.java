package com.modeltech.datamasteryhub.modules.communication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestimonialRequestDTO {
    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    @NotBlank(message = "Le role est obligatoire")
    private String role;

    @NotBlank(message = "Le message est obligatoire")
    private String content;
 
    private String rating;
    
    private String company;

}
