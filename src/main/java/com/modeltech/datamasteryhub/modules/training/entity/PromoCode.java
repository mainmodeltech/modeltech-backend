package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Code promo / parrainage permettant une reduction sur inscription.
 */
@Entity
@Table(name = "promo_codes")
@Getter @Setter @NoArgsConstructor
public class PromoCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    private String description;

    // Parrain
    @Column(name = "referrer_name", nullable = false)
    private String referrerName;

    @Column(name = "referrer_email")
    private String referrerEmail;

    @Column(name = "referrer_phone", length = 50)
    private String referrerPhone;

    // Reduction
    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent = 0;

    // Limites
    @Column(name = "max_uses")
    private Integer maxUses;          // null = illimite

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // null = pas d'expiration

    // Etat
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
