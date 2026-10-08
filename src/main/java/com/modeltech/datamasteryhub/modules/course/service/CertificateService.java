package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.CertificatePayloads;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Certificats de réussite : délivrance automatique dès que toutes les conditions de la formation sont remplies,
 * délivrance manuelle (avec dérogation motivée), révocation, vérification publique et PDF.
 */
public interface CertificateService {

    /**
     * Délivre le certificat si l'apprenant remplit toutes les conditions et n'en a pas déjà un de valide.
     *
     * @return le numéro du certificat délivré, vide si rien n'a été délivré
     */
    Optional<String> issueIfEligible(UUID learnerId, UUID bootcampId);

    /** Délivrance depuis la page de suivi de session ; {@code force} exige un rôle ADMIN et un motif. */
    CertificatePayloads.AdminCertificate issueManually(UUID sessionId, UUID learnerId, CertificatePayloads.IssueRequest request,
                                                       String actorEmail, Collection<String> actorRoles);

    CertificatePayloads.AdminCertificate revoke(String publicId, String reason, String actorEmail);

    /** Renvoie l'e-mail « certificat prêt » (avec le PDF). */
    void resend(String publicId);

    Page<CertificatePayloads.AdminCertificate> findAllForAdmin(String status, Pageable pageable);

    /** Vérification publique ; 404 pour tout numéro inconnu. */
    CertificatePayloads.PublicCertificate verify(String publicId);

    /** PDF d'un certificat valide (410 s'il est révoqué). */
    byte[] pdf(String publicId);

    List<CertificatePayloads.LearnerCertificate> findForLearner(String learnerEmail);

    /** Envoie l'e-mail de félicitations d'un certificat tout juste délivré. */
    void notifyIssued(String publicId);

    /** Passe en revue les accès en cours et délivre les certificats devenus dus ; retourne le nombre délivré. */
    int sweep();
}
