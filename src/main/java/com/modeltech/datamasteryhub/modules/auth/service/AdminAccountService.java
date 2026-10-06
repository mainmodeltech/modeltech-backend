package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.request.CreateAdminUserRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.UpdateAdminUserRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AdminUserSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Gestion des comptes de back-office (réservée aux SUPER_ADMIN). */
public interface AdminAccountService {

    Page<AdminUserSummaryResponse> findAll(Pageable pageable);

    AdminUserSummaryResponse findById(UUID id);

    /** Crée le compte (sans mot de passe utilisable) et envoie l'invitation « définir mon mot de passe ». */
    AdminUserSummaryResponse create(CreateAdminUserRequest request);

    /**
     * Met à jour le compte. Garde-fous : on ne peut ni se désactiver, ni se retirer le rôle
     * SUPER_ADMIN, ni laisser la plateforme sans SUPER_ADMIN actif.
     *
     * @param actingEmail e-mail du SUPER_ADMIN qui effectue la modification
     */
    AdminUserSummaryResponse update(UUID id, UpdateAdminUserRequest request, String actingEmail);
}
