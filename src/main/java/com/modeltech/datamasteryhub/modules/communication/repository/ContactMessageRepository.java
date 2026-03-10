package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    /** Liste paginée — non supprimés, triée par createdAt DESC via le Pageable. */
    Page<ContactMessage> findAllByIsDeletedFalse(Pageable pageable);

    /** Ancienne méthode — conservée si utilisée ailleurs. */
    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    Optional<ContactMessage> findByIdAndIsDeletedFalse(UUID id);
}