package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.config.InvoiceProperties;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.notification.service.InvoiceNotice;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.dto.InvoicePayloads;
import com.modeltech.datamasteryhub.modules.training.entity.Invoice;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.repository.InvoiceRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import com.modeltech.datamasteryhub.modules.training.service.InvoicePdfGenerator;
import com.modeltech.datamasteryhub.modules.training.service.InvoiceService;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationPricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private static final Set<RegistrationStatus> INVOICEABLE = Set.of(RegistrationStatus.PAYMENT_PENDING,
            RegistrationStatus.PAYMENT_TO_CONFIRM, RegistrationStatus.CONFIRMED, RegistrationStatus.COMPLETED);

    private final InvoiceRepository invoiceRepository;
    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final RegistrationPricing pricing;
    private final InvoicePdfGenerator pdfGenerator;
    private final InvoiceProperties properties;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public InvoicePayloads.InvoiceResponse create(UUID registrationId, InvoicePayloads.CreateRequest request, String actorEmail) {
        Registration reg = registrationRepository.findByIdAndIsDeletedFalse(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", registrationId));
        if (reg.getTotalAmount() == null || !INVOICEABLE.contains(reg.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une candidature acceptée (avec un montant) peut être facturée.");
        }
        if (invoiceRepository.findByRegistrationIdAndStatusAndIsDeletedFalse(registrationId, Invoice.ISSUED).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une facture est déjà en vigueur : annulez-la avant d'en émettre une nouvelle.");
        }

        List<Payment> payments = paymentRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(registrationId)
                .stream().filter(p -> p.getStatus() != PaymentStatus.CANCELLED).toList();
        LocalDate today = LocalDate.now();

        Invoice inv = new Invoice();
        inv.setRegistration(reg);
        inv.setIssueDate(today);
        inv.setNumber(nextNumber(today.getYear()));
        inv.setBuyerName(firstNonBlank(request.getBuyerName(), reg.getCompany(), (reg.getFirstName() + " " + reg.getLastName()).trim()));
        inv.setBuyerContact(firstNonBlank(request.getBuyerContact(), (reg.getFirstName() + " " + reg.getLastName()).trim()));
        inv.setBuyerEmail(firstNonBlank(request.getBuyerEmail(), reg.getEmail()));
        inv.setBuyerAddress(blankToNull(request.getBuyerAddress()));
        inv.setPurchaseOrderRef(firstNonBlank(request.getPurchaseOrderRef(), payments.stream()
                .map(Payment::getPurchaseOrderRef).filter(s -> s != null && !s.isBlank()).findFirst().orElse(null)));
        inv.setNotes(blankToNull(request.getNotes()));
        inv.setCurrency(payments.isEmpty() ? "XOF" : payments.get(0).getCurrency());
        fillAmounts(inv, reg);
        inv.setInstallments(payments.stream()
                .map(p -> new Invoice.Installment(p.getInstallmentNumber(),
                        (p.getDueDate() != null ? p.getDueDate() : today).toString(), p.getAmount()))
                .toList());
        inv.setDueDate(request.getDueDate() != null ? request.getDueDate()
                : payments.stream().map(Payment::getDueDate).filter(Objects::nonNull).min(Comparator.naturalOrder())
                        .orElse(today.plusDays(properties.getDefaultDueDays())));
        inv.setSeller(sellerSnapshot());
        inv.setStatus(Invoice.ISSUED);

        Invoice saved = invoiceRepository.save(inv);
        log.info("Facture {} émise par {} pour l'inscription {} ({} {})", saved.getNumber(), actorEmail, registrationId,
                saved.getTotal(), saved.getCurrency());
        return toResponse(saved);
    }

    @Override
    public List<InvoicePayloads.InvoiceResponse> findByRegistration(UUID registrationId) {
        return invoiceRepository.findAllByRegistrationIdAndIsDeletedFalseOrderByIssueDateDescCreatedAtDesc(registrationId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public Page<InvoicePayloads.InvoiceResponse> findAll(String status, Pageable pageable) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return invoiceRepository.search(normalized, pageable).map(this::toResponse);
    }

    @Override
    public byte[] pdf(String number) {
        return pdfGenerator.generate(requireByNumber(number));
    }

    @Override
    @Transactional
    public InvoicePayloads.InvoiceResponse cancel(String number, String reason, String actorEmail) {
        Invoice inv = requireByNumber(number);
        if (!inv.isIssued()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette facture est déjà annulée.");
        inv.setStatus(Invoice.CANCELLED);
        inv.setCancelledAt(LocalDateTime.now());
        inv.setCancelledReason(reason.trim());
        log.info("Facture {} annulée par {} : {}", number, actorEmail, reason);
        return toResponse(invoiceRepository.save(inv));
    }

    @Override
    public void send(String number, String to) {
        Invoice inv = requireByNumber(number);
        if (!inv.isIssued()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette facture est annulée.");
        String recipient = firstNonBlank(to, inv.getBuyerEmail(), inv.getRegistration().getEmail());
        notificationService.sendInvoiceEmail(new InvoiceNotice(recipient, inv.getBuyerContact() != null ? inv.getBuyerContact() : inv.getBuyerName(),
                inv.getNumber(), inv.getTotal(), inv.getCurrency(), inv.getDueDate(), pdfGenerator.generate(inv)));
    }

    @Override
    public byte[] pdfForPaymentToken(String paymentToken) {
        Payment payment = paymentRepository.findByPublicTokenAndIsDeletedFalse(paymentToken)
                .filter(p -> p.getTokenExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Lien de paiement invalide."));
        Invoice inv = invoiceRepository.findByRegistrationIdAndStatusAndIsDeletedFalse(payment.getRegistration().getId(), Invoice.ISSUED)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune facture pour ce paiement."));
        return pdfGenerator.generate(inv);
    }

    // ── Privé ───────────────────────────────────────────────────────

    /** Prix, remise et TVA : le total facturé est toujours le montant à payer de l'inscription. */
    private void fillAmounts(Invoice inv, Registration reg) {
        long total = reg.getTotalAmount();
        StringBuilder description = new StringBuilder(reg.getBootcampTitle() != null ? reg.getBootcampTitle() : "Formation");
        if (reg.getSessionName() != null && !reg.getSessionName().isBlank()) description.append(" — ").append(reg.getSessionName());

        Optional<RegistrationPricing.Quote> quote = pricing.quote(reg).filter(q -> q.total() == total);
        if (quote.isPresent()) {
            RegistrationPricing.Quote q = quote.get();
            if (q.earlyBird()) description.append(" (tarif early-bird)");
            inv.setUnitAmount(q.baseAmount());
            inv.setDiscountAmount(q.discountAmount());
            if (q.discountAmount() > 0) {
                inv.setDiscountLabel("Remise" + (reg.getPromoCodeUsed() != null ? " code promo " + reg.getPromoCodeUsed() : "")
                        + " (−" + q.discountPercent() + " %)");
            }
        } else {
            // Montant négocié par l'administration : une seule ligne, sans détail de remise
            inv.setUnitAmount(total);
            inv.setDiscountAmount(0L);
        }
        inv.setDescription(description.length() > 500 ? description.substring(0, 500) : description.toString());

        BigDecimal vat = properties.getVatPercent() == null ? BigDecimal.ZERO : properties.getVatPercent();
        long excl = vat.signum() > 0
                ? BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(100).add(vat), 0, RoundingMode.HALF_UP).longValue()
                : total;
        inv.setVatPercent(vat);
        inv.setTotalExclVat(excl);
        inv.setVatAmount(total - excl);
        inv.setTotal(total);
    }

    private Map<String, String> sellerSnapshot() {
        InvoiceProperties.Seller s = properties.getSeller();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", nullToEmpty(s.getName()));
        m.put("address", nullToEmpty(s.getAddress()));
        m.put("phone", nullToEmpty(s.getPhone()));
        m.put("email", nullToEmpty(s.getEmail()));
        m.put("website", nullToEmpty(s.getWebsite()));
        m.put("taxId", nullToEmpty(s.getTaxId()));
        m.put("registerNumber", nullToEmpty(s.getRegisterNumber()));
        m.put("paymentDetails", nullToEmpty(s.getPaymentDetails()));
        m.put("footer", nullToEmpty(s.getFooter()));
        return m;
    }

    /** Numéro continu par année : le compteur est verrouillé jusqu'à la validation de la transaction (pas de trou). */
    private String nextNumber(int year) {
        Integer n = jdbc.queryForObject("""
                INSERT INTO invoice_counters (year, last_number) VALUES (?, 1)
                ON CONFLICT (year) DO UPDATE SET last_number = invoice_counters.last_number + 1
                RETURNING last_number
                """, Integer.class, year);
        return "%s-%d-%05d".formatted(properties.getNumberPrefix(), year, n);
    }

    private Invoice requireByNumber(String number) {
        return invoiceRepository.findByNumberAndIsDeletedFalse(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Facture", "numéro", number));
    }

    private InvoicePayloads.InvoiceResponse toResponse(Invoice i) {
        return InvoicePayloads.InvoiceResponse.builder()
                .id(i.getId()).number(i.getNumber()).registrationId(i.getRegistration().getId()).status(i.getStatus())
                .issueDate(i.getIssueDate()).dueDate(i.getDueDate())
                .buyerName(i.getBuyerName()).buyerContact(i.getBuyerContact()).buyerEmail(i.getBuyerEmail())
                .purchaseOrderRef(i.getPurchaseOrderRef()).description(i.getDescription())
                .unitAmount(i.getUnitAmount()).discountAmount(i.getDiscountAmount())
                .totalExclVat(i.getTotalExclVat()).vatAmount(i.getVatAmount()).total(i.getTotal()).currency(i.getCurrency())
                .cancelledAt(i.getCancelledAt()).cancelledReason(i.getCancelledReason())
                .pdfPath("/api/v1/admin/invoices/" + i.getNumber() + "/pdf")
                .build();
    }

    private String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
