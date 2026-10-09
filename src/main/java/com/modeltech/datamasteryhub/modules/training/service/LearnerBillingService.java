package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.LearnerBillingPayloads;

import java.util.List;
import java.util.UUID;

/** Paiements, reçus et attestations de l'apprenant connecté. */
public interface LearnerBillingService {

    List<LearnerBillingPayloads.PaymentItem> payments(String learnerEmail);

    List<LearnerBillingPayloads.EnrollmentItem> enrollments(String learnerEmail);

    /** PDF du reçu d'une de ses échéances confirmées ; 404 si elle n'est pas à lui ou pas confirmée. */
    byte[] receipt(String learnerEmail, UUID paymentId);

    /** PDF du reçu, par le jeton du lien de paiement (candidat sans compte) ; 404 si pas confirmée. */
    byte[] receiptByToken(String paymentToken);

    /** Attestation d'inscription à une formation dont l'inscription est confirmée. */
    byte[] attestation(String learnerEmail, UUID formationId);
}
