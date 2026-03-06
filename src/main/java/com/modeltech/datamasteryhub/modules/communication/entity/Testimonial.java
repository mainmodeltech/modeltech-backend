package com.modeltech.datamasteryhub.modules.communication.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Table(name = "testimonials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class Testimonial extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name="company", nullable = false)
    private String company;

    @Column(name="content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name="rating")
    private Integer rating;

    @Column(name="published")
    private Boolean published;

     @Column(name="display_order")
     private Integer display_order;


}
