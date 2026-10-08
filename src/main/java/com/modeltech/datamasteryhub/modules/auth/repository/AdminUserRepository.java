package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByEmailAndIsDeletedFalse(String email);

    boolean existsByEmailAndIsDeletedFalse(String email);

    /** Comptes actifs ayant un rôle donné (liste des formateurs à affecter). */
    List<AdminUser> findAllByRolesNameAndActiveTrueAndIsDeletedFalseOrderByFullNameAsc(String roleName);

    Optional<AdminUser> findByIdAndIsDeletedFalse(UUID id);

    Page<AdminUser> findAllByIsDeletedFalse(Pageable pageable);

    /** Nombre de comptes actifs portant un rôle (garde-fou « dernier SUPER_ADMIN »). */
    long countByRolesNameAndActiveTrueAndIsDeletedFalse(String roleName);
}