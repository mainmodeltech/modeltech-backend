package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bootcamps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bootcamp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    private String duration;

    private String audience;

    private String prerequisites;

    private String price;

    @Column(name = "next_session")
    private String nextSession;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "benefits", columnDefinition = "text[]")
    private List<String> benefits = new ArrayList<>();

    @Builder.Default
    private boolean featured = false;

    @Builder.Default
    private boolean published = true;
}
