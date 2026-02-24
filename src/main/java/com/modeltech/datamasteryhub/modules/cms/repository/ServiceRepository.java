package com.modeltech.datamasteryhub.modules.cms.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.cms.entity.Service;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends SoftDeleteRepository<Service, UUID> {

    /** Services publies et non supprimes, tries par ordre d'affichage */
    List<Service> findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc();

    /** Service publie par ID */
    Optional<Service> findByIdAndPublishedTrueAndIsDeletedFalse(UUID id);
}
