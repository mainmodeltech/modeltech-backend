package com.modeltech.datamasteryhub.modules.payments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Réglage général des paiements (une seule ligne) : l'interrupteur du paiement en ligne, utilisable en urgence. */
@Entity
@Table(name = "payment_setting")
@Getter @Setter @NoArgsConstructor
public class PaymentSetting {

    public static final short SINGLETON = 1;

    @Id
    private Short id = SINGLETON;

    @Column(name = "online_enabled", nullable = false)
    private boolean onlineEnabled = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
