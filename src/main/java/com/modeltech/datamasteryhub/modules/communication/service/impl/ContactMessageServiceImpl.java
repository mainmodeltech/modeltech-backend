package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import com.modeltech.datamasteryhub.modules.communication.mapper.ContactMessageMapper;
import com.modeltech.datamasteryhub.modules.communication.repository.ContactMessageRepository;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactMessageServiceImpl implements ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;
    private final ContactMessageMapper contactMessageMapper;
    private final NotificationService notificationService;

    // ── Public ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ContactMessageResponseDTO saveMessage(ContactMessageRequestDTO dto) {
        ContactMessage message = contactMessageMapper.toEntity(dto);
        message.setStatus(ContactMessageStatus.unread);
        ContactMessage saved = contactMessageRepository.save(message);

        // Notification asynchrone — ne bloque pas la réponse API
        notificationService.notifyNewContactMessage(saved);

        return contactMessageMapper.toResponseDto(saved);
    }

    // ── Admin ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ContactMessageResponseDTO> getAllMessages(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contactMessageRepository
                .findAllByIsDeletedFalse(pageable)
                .map(contactMessageMapper::toResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ContactMessageResponseDTO findByIdForAdmin(UUID id) {
        ContactMessage contactMessage = contactMessageRepository
                .findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", "id", id));
        return contactMessageMapper.toResponseDto(contactMessage);
    }

    @Override
    @Transactional
    public ContactMessageResponseDTO updateStatus(UUID id, ContactMessageStatus status) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", "id", id));
        message.setStatus(status);
        return contactMessageMapper.toResponseDto(contactMessageRepository.save(message));
    }

    @Override
    @Transactional
    public ContactMessageResponseDTO updateMessage(UUID id, UpdateMessageRequestDTO dto) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", "id", id));

        message.setFirstName(dto.getFirstName());
        message.setLastName(dto.getLastName());
        message.setEmail(dto.getEmail());
        message.setCompany(dto.getCompany());
        message.setMessage(dto.getMessage());

        return contactMessageMapper.toResponseDto(contactMessageRepository.save(message));
    }
}
