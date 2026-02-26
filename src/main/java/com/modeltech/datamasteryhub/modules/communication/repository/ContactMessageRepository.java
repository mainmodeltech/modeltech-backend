package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    List<ContactMessage> findAllByOrderByCreatedAtDesc();
    Optional<ContactMessage> findByIdAndIsDeletedFalse(UUID id);
}