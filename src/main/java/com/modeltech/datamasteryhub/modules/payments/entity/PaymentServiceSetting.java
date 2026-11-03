package com.modeltech.datamasteryhub.modules.payments.entity;

import com.modeltech.datamasteryhub.modules.payments.PayableService;
import com.modeltech.datamasteryhub.modules.payments.PaymentMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Moyens de paiement choisis pour un service. */
@Entity
@Table(name = "payment_service_setting")
@Getter @Setter @NoArgsConstructor
public class PaymentServiceSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PayableService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMode mode = PaymentMode.MANUAL_ONLY;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
