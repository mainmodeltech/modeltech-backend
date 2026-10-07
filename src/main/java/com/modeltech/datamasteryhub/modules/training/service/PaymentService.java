package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.AcceptRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.DeclarePaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.ManualPaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.EnrollmentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.PublicPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Parcours candidature → paiement → accès :
 * <pre>
 * PENDING --accept--> PAYMENT_PENDING --déclaration--> PAYMENT_TO_CONFIRM --confirm--> CONFIRMED
 *    \--reject--> REJECTED                    \--paiement refusé--/
 * </pre>
 * À la première échéance confirmée : place comptée, compte apprenant créé (invitation),
 * accès à la session ouvert, e-mail « place confirmée ».
 */
public interface PaymentService {

    // ── Candidatures (admin) ─────────────────────────────────────────

    RegistrationResponse accept(UUID registrationId, AcceptRegistrationRequest request, String actor);

    RegistrationResponse rejectRegistration(UUID registrationId, String reason);

    /** Enregistre, pour le candidat, un paiement reçu hors du site : l'échéance passe à DECLARED. */
    AdminPaymentResponse recordManualPayment(UUID registrationId, ManualPaymentRequest request);

    // ── Paiements (admin) ────────────────────────────────────────────

    Page<AdminPaymentResponse> findAllForAdmin(PaymentStatus status, UUID registrationId, Pageable pageable);

    AdminPaymentResponse confirm(UUID paymentId, String actor);

    AdminPaymentResponse rejectPayment(UUID paymentId, String reason);

    AdminPaymentResponse remind(UUID paymentId);

    Page<EnrollmentResponse> findEnrollments(Pageable pageable);

    // ── Lien de paiement (public, authentifié par le jeton) ──────────

    PublicPaymentResponse getByToken(String token);

    PublicPaymentResponse declare(String token, DeclarePaymentRequest request);

    PublicPaymentResponse uploadProof(String token, MultipartFile file);

    // ── Relances automatiques ────────────────────────────────────────

    /** Envoie les relances dues à l'instant {@code now} ; retourne le nombre d'e-mails émis. */
    int sendDueReminders(LocalDateTime now);
}
