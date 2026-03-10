package com.modeltech.datamasteryhub.modules.communication.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "testimonials")
@Getter @Setter @NoArgsConstructor
public class Testimonial extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String company;

    private String role;

    /** Ex: "Power BI", "Data Analyst" */
    private String bootcamp;

    /** Ex: "Embauché en 3 mois après le bootcamp" */
    private String result;

    @Column(nullable = false)
    private Integer rating = 5;

    @Column(nullable = false)
    private Boolean published = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}