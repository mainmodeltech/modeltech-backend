package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import com.modeltech.datamasteryhub.modules.communication.mapper.ContactMessageMapper;
import com.modeltech.datamasteryhub.modules.communication.repository.ContactMessageRepository;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactMessageServiceImpl implements ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;
    private final ContactMessageMapper contactMessageMapper;

    @Override
    @Transactional
    public ContactMessageResponseDTO saveMessage(ContactMessageRequestDTO dto) {
        ContactMessage message = contactMessageMapper.toEntity(dto);
        message.setStatus(ContactMessageStatus.unread);

        return contactMessageMapper.toResponseDto(contactMessageRepository.save(message));
    }

    @Override
    public List<ContactMessageResponseDTO> getAllMessages() {
        return contactMessageRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(contactMessageMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ContactMessageResponseDTO updateStatus(UUID id, ContactMessageStatus status) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message non trouvé"));
        message.setStatus(status);
        return contactMessageMapper.toResponseDto(contactMessageRepository.save(message));
    }

    @Override
    public ContactMessageResponseDTO findByIdForAdmin(UUID id) {
        ContactMessage contactMessage = contactMessageRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp", "id", id));
        return contactMessageMapper.toResponseDto(contactMessage);
    }

    @Override
    @Transactional
    public ContactMessageResponseDTO updateMessage(UUID id, UpdateMessageRequestDTO updateMessageRequestDTO) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message non trouvé"));


        message.setFirstName(updateMessageRequestDTO.getFirstName());
        message.setLastName(updateMessageRequestDTO.getLastName());
        message.setEmail(updateMessageRequestDTO.getEmail());
        message.setCompany(updateMessageRequestDTO.getCompany());
        message.setMessage(updateMessageRequestDTO.getMessage());

        return contactMessageMapper.toResponseDto(contactMessageRepository.save(message));
    }
}
