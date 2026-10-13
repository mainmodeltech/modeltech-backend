package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AdminRecoveryCodeRepository extends JpaRepository<AdminRecoveryCode, UUID> {

    Optional<AdminRecoveryCode> findByAdminUserIdAndCodeHashAndUsedAtIsNull(UUID adminUserId, String codeHash);

    long countByAdminUserIdAndUsedAtIsNull(UUID adminUserId);

    @Modifying
    @Query("DELETE FROM AdminRecoveryCode c WHERE c.adminUserId = :adminUserId")
    void deleteAllFor(UUID adminUserId);
}
