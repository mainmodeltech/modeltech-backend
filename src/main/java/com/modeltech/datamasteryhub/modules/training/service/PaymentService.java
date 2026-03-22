package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.SessionFinancialSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    /** Enregistrer un paiement sur une inscription */
    PaymentResponse addPayment(UUID registrationId, CreatePaymentRequest request);

    /** Lister les paiements d'une inscription */
    List<PaymentResponse> findByRegistration(UUID registrationId);

    /** Supprimer (soft) un paiement */
    void deletePayment(UUID paymentId);

    /** Resume financier d'une session */
    SessionFinancialSummaryResponse getFinancialSummary(UUID sessionId);
}
