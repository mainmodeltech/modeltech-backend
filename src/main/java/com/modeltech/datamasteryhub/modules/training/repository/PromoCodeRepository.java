package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.PromoCode;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromoCodeRepository extends SoftDeleteRepository<PromoCode, UUID> {

    /** Chercher un code actif et non supprime (pour validation a l'inscription) */
    Optional<PromoCode> findByCodeAndIsActiveTrueAndIsDeletedFalse(String code);
}
