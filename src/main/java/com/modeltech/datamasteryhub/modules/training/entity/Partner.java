package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Partenaire formateur. {@code revenueSharePercent} et les champs de contact
 * sont à usage interne : ils ne doivent jamais sortir sur l'API publique.
 */
@Entity
@Table(name = "partners")
@Getter @Setter @NoArgsConstructor
public class Partner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "logo_object_key")
    private String logoObjectKey;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String website;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "revenue_share_percent", precision = 5, scale = 2)
    private BigDecimal revenueSharePercent;

    @Column(nullable = false)
    private Boolean active = true;
}
