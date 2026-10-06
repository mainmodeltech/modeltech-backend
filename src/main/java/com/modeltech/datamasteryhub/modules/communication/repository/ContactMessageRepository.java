package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.communication.enums.ContactType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    /** Liste paginée — non supprimés, triée par createdAt DESC via le Pageable. */
    Page<ContactMessage> findAllByIsDeletedFalse(Pageable pageable);

    /** Liste paginée filtrée sur un type de message (contact, diagnostic, candidature partenaire). */
    Page<ContactMessage> findAllByTypeAndIsDeletedFalse(ContactType type, Pageable pageable);

    /** Ancienne méthode — conservée si utilisée ailleurs. */
    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    Optional<ContactMessage> findByIdAndIsDeletedFalse(UUID id);
}