package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.InvoicePayloads;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Factures PDF des entreprises (numérotation continue, document figé à l'émission). */
public interface InvoiceService {

    /** Émet la facture d'une inscription acceptée ; 409 si une facture en vigueur existe déjà. */
    InvoicePayloads.InvoiceResponse create(UUID registrationId, InvoicePayloads.CreateRequest request, String actorEmail);

    List<InvoicePayloads.InvoiceResponse> findByRegistration(UUID registrationId);

    Page<InvoicePayloads.InvoiceResponse> findAll(String status, Pageable pageable);

    byte[] pdf(String number);

    InvoicePayloads.InvoiceResponse cancel(String number, String reason, String actorEmail);

    /** Envoie la facture (PDF joint) par e-mail. */
    void send(String number, String to);

    /** PDF de la facture en vigueur d'une inscription, depuis son lien de paiement (404 s'il n'y en a pas). */
    byte[] pdfForPaymentToken(String paymentToken);
}
