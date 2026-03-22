package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Paiement lie a une inscription.
 * Une inscription peut avoir 1 ou 2 paiements (paiement en tranches).
 */
@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    /** Montant en FCFA */
    @Column(nullable = false)
    private Integer amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    /** Reference de la transaction (Wave, OM, virement bancaire, etc.) */
    @Column(length = 255)
    private String reference;

    /** Notes libres */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Admin qui a enregistre le paiement */
    @Column(name = "recorded_by")
    private String recordedBy;
}
