package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BootcampRepository extends JpaRepository<Bootcamp, UUID> {

    // Public : uniquement les publiés et non supprimés
    List<Bootcamp> findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc();

    // Admin : tous (hors soft-deleted)
    List<Bootcamp> findAllByIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc();

    // Détail public
    Optional<Bootcamp> findByIdAndPublishedTrueAndIsDeletedFalse(UUID id);

    // Check unicité du titre
    boolean existsByTitleIgnoreCaseAndIsDeletedFalse(String title);
    boolean existsByTitleIgnoreCaseAndIsDeletedFalseAndIdNot(String title, UUID id);
}
