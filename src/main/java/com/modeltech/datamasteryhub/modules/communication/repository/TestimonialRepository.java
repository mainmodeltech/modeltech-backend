package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.modules.communication.entity.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestimonialRepository extends JpaRepository<Testimonial, UUID> {

    /** Tous les témoignages publiés, triés par displayOrder puis date de création */
    List<Testimonial> findByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc();

    /** Tous les témoignages non supprimés (admin) */
    List<Testimonial> findByIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc();

    /** Témoignage à afficher sur la fiche d'un bootcamp donné */
    Optional<Testimonial> findFirstByBootcampRefIdAndPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc(UUID bootcampId);
}
