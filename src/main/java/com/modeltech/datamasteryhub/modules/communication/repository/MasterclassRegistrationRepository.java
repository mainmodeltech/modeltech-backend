package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.modules.communication.entity.MasterclassRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MasterclassRegistrationRepository extends JpaRepository<MasterclassRegistration, UUID> {

    boolean existsByMasterclassIdAndEmailAndIsDeletedFalse(String masterclassId, String email);

    Page<MasterclassRegistration> findByMasterclassIdAndIsDeletedFalse(String masterclassId, Pageable pageable);

    long countByMasterclassIdAndIsDeletedFalse(String masterclassId);

    Optional<MasterclassRegistration> findByMasterclassIdAndEmailAndIsDeletedFalse(String masterclassId, String email);
}
