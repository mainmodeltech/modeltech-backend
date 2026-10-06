package com.modeltech.datamasteryhub.modules.communication.service;

import com.modeltech.datamasteryhub.modules.communication.dto.request.NewsletterSubscribeRequest;
import com.modeltech.datamasteryhub.modules.communication.dto.response.AdminNewsletterSubscriptionResponse;
import com.modeltech.datamasteryhub.modules.communication.enums.NewsletterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NewsletterService {

    /**
     * Demande d'abonnement (double opt-in). Ne révèle jamais si l'adresse est déjà
     * connue : le comportement visible est identique dans tous les cas.
     */
    void subscribe(NewsletterSubscribeRequest request);

    /** Confirme l'abonnement via le jeton reçu par e-mail (idempotent). */
    void confirm(String token);

    /** Désinscrit via le jeton de désinscription (idempotent). */
    void unsubscribe(String token);

    /** Liste admin des abonnés, filtrable par statut (null = tous). */
    Page<AdminNewsletterSubscriptionResponse> findAllForAdmin(NewsletterStatus status, Pageable pageable);
}
