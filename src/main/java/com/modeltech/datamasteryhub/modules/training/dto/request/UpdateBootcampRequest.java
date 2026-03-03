package com.modeltech.datamasteryhub.modules.training.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class UpdateBootcampRequest {
    private String title;
    private String description;
    private String duration;
    private String audience;
    private String prerequisites;
    private String price;
    private List<String> benefits;
    private String category;
    private String tag;
    private String iconName;
    private Boolean featured;
    private Boolean published;
    private Integer displayOrder;
}