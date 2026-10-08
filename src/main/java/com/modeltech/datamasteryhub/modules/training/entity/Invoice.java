package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facture d'une inscription (entreprises). Document comptable : tout est figé à l'émission
 * (acheteur, montants, échéances, coordonnées du vendeur) ; une erreur se corrige par annulation puis nouvelle facture.
 */
@Entity
@Table(name = "invoices")
@Getter @Setter @NoArgsConstructor
public class Invoice extends BaseEntity {

    public static final String ISSUED = "ISSUED";
    public static final String CANCELLED = "CANCELLED";

    /** Une échéance imprimée sur la facture. */
    public record Installment(int number, String dueDate, long amount) {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 30)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, updatable = false)
    private Registration registration;

    @Column(name = "issue_date", nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 10)
    private String status = ISSUED;

    @Column(name = "buyer_name", nullable = false, updatable = false)
    private String buyerName;

    @Column(name = "buyer_contact", updatable = false)
    private String buyerContact;

    @Column(name = "buyer_email", updatable = false)
    private String buyerEmail;

    @Column(name = "buyer_address", length = 500, updatable = false)
    private String buyerAddress;

    @Column(name = "purchase_order_ref", updatable = false)
    private String purchaseOrderRef;

    @Column(nullable = false, length = 500, updatable = false)
    private String description;

    @Column(name = "unit_amount", nullable = false, updatable = false)
    private Long unitAmount;

    @Column(name = "discount_label", updatable = false)
    private String discountLabel;

    @Column(name = "discount_amount", nullable = false, updatable = false)
    private Long discountAmount = 0L;

    @Column(name = "vat_percent", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal vatPercent = BigDecimal.ZERO;

    @Column(name = "total_excl_vat", nullable = false, updatable = false)
    private Long totalExclVat;

    @Column(name = "vat_amount", nullable = false, updatable = false)
    private Long vatAmount = 0L;

    /** Montant à payer (TVA incluse) : égal au total de l'inscription. */
    @Column(nullable = false, updatable = false)
    private Long total;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "XOF";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<Installment> installments;

    /** Coordonnées du vendeur au jour de l'émission. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, String> seller;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String notes;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    public boolean isIssued() {
        return ISSUED.equals(status);
    }
}
