package com.modeltech.datamasteryhub.modules.communication.service;

import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import java.util.List;
import java.util.UUID;

public interface ContactMessageService {
    ContactMessageResponseDTO saveMessage(ContactMessageRequestDTO dto);
    ContactMessageResponseDTO updateMessage(UUID id, UpdateMessageRequestDTO dto);
    List<ContactMessageResponseDTO> getAllMessages();
    ContactMessageResponseDTO updateStatus(UUID id, ContactMessageStatus status);
    ContactMessageResponseDTO findByIdForAdmin(UUID id);
}
