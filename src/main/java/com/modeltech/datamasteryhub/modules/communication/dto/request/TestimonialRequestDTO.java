package com.modeltech.datamasteryhub.modules.communication.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class TestimonialRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    @NotBlank(message = "Le témoignage est obligatoire")
    private String content;

    private String company;

    private String role;

    /** Ex: "Power BI", "Python & SQL" — conservé pour compatibilité, préférer bootcampId */
    private String bootcamp;

    /** Lien vers le bootcamp concerné, pour affichage sur sa fiche formation */
    private UUID bootcampId;

    /** Ex: "Embauché 3 mois après le bootcamp" */
    private String result;

    @Min(value = 1, message = "La note minimale est 1")
    @Max(value = 5, message = "La note maximale est 5")
    private Integer rating = 5;

    private Boolean published = true;

    private Integer displayOrder = 0;
}
