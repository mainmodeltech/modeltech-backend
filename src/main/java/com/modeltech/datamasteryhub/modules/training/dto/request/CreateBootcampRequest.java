package com.modeltech.datamasteryhub.modules.training.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateBootcampRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String description;
    private String duration;
    private String audience;
    private String prerequisites;
    private String price;
    private List<String> benefits;
    private String category = "data";
    private String tag;
    private String iconName;
    private Boolean featured = false;
    private Boolean published = true;
    private Integer displayOrder = 0;
}
