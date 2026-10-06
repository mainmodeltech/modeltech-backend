package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.request.CreateLearnerRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.LearnerResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LearnerService {

    // Admin
    Page<LearnerResponse> findAllForAdmin(Pageable pageable);
    LearnerResponse findByIdForAdmin(UUID id);
    LearnerResponse create(CreateLearnerRequest request);
    LearnerResponse setActive(UUID id, boolean active);
    void resendInvitation(UUID id);

    /**
     * Retourne le compte apprenant de cet e-mail, ou le crée (rôle LEARNER, sans mot de passe)
     * et envoie l'invitation « définir mon mot de passe ». Utilisé à la confirmation du paiement
     * d'une inscription : une même personne qui s'inscrit à plusieurs formations garde un seul compte.
     *
     * @throws org.springframework.web.server.ResponseStatusException 409 si l'e-mail est celui d'un compte de back-office
     */
    Learner findOrCreateInvited(String firstName, String lastName, String email, String phone, String country);
}
