package com.modeltech.datamasteryhub.modules.communication.service;

import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ContactMessageService {

    /** Sauvegarde un message et déclenche les notifications (Slack + Email). */
    ContactMessageResponseDTO saveMessage(ContactMessageRequestDTO dto);

    /** Liste paginée de tous les messages non supprimés, triée par date décroissante. */
    Page<ContactMessageResponseDTO> getAllMessages(int page, int size);

    /** Retourne un message par son id (hors supprimés). */
    ContactMessageResponseDTO findByIdForAdmin(UUID id);

    /** Met à jour le statut d'un message. */
    ContactMessageResponseDTO updateStatus(UUID id, ContactMessageStatus status);

    /** Met à jour le contenu d'un message. */
    ContactMessageResponseDTO updateMessage(UUID id, UpdateMessageRequestDTO dto);
}
