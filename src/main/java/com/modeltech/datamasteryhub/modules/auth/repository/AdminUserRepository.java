package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByEmailAndIsDeletedFalse(String email);

    boolean existsByEmailAndIsDeletedFalse(String email);

    Optional<AdminUser> findByIdAndIsDeletedFalse(UUID id);

    Page<AdminUser> findAllByIsDeletedFalse(Pageable pageable);

    /** Nombre de comptes actifs portant un rôle (garde-fou « dernier SUPER_ADMIN »). */
    long countByRolesNameAndActiveTrueAndIsDeletedFalse(String roleName);
}