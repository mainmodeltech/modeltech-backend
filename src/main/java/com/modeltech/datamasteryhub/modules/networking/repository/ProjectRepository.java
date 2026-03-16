package com.modeltech.datamasteryhub.modules.networking.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.networking.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends SoftDeleteRepository<Project, UUID> {

    /** Page publique : projets publiés triés par display_order */
    List<Project> findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscYearDesc();

    /** Admin : tous les projets non supprimés */
    Page<Project> findAllByIsDeletedFalse(Pageable pageable);
}
