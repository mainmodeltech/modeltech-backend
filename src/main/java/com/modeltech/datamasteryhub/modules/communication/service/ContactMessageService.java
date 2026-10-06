package com.modeltech.datamasteryhub.modules.communication.service;

import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.DiagnosticRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.PartnerApplicationRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import com.modeltech.datamasteryhub.modules.communication.enums.ContactType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ContactMessageService {

    /** Sauvegarde un message et déclenche les notifications (Slack + Email). */
    ContactMessageResponseDTO saveMessage(ContactMessageRequestDTO dto);

    /** Demande de diagnostic entreprise : stockée comme un message de type DIAGNOSTIC. */
    ContactMessageResponseDTO saveDiagnosticRequest(DiagnosticRequestDTO dto);

    /** Candidature partenaire : stockée comme un message de type PARTNER_APPLICATION. */
    ContactMessageResponseDTO savePartnerApplication(PartnerApplicationRequestDTO dto);

    /** Liste paginée de tous les messages non supprimés, triée par date décroissante. */
    Page<ContactMessageResponseDTO> getAllMessages(int page, int size);

    /** Idem, filtrée sur un type de message (null = tous les types). */
    Page<ContactMessageResponseDTO> getAllMessages(int page, int size, ContactType type);

    /** Retourne un message par son id (hors supprimés). */
    ContactMessageResponseDTO findByIdForAdmin(UUID id);

    /** Met à jour le statut d'un message. */
    ContactMessageResponseDTO updateStatus(UUID id, ContactMessageStatus status);

    /** Met à jour le contenu d'un message. */
    ContactMessageResponseDTO updateMessage(UUID id, UpdateMessageRequestDTO dto);
}
