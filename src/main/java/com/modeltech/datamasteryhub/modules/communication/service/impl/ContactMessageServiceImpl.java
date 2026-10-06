package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.DiagnosticRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.PartnerApplicationRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import com.modeltech.datamasteryhub.modules.communication.enums.ContactType;
import com.modeltech.datamasteryhub.modules.communication.enums.RequesterType;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactMessageServiceImpl implements ContactMessageService {

    static final String DIAGNOSTIC_SUBJECT = "Diagnostic entreprise";
    static final String PARTNER_APPLICATION_SUBJECT = "Candidature partenaire formateur";

    private final ContactMessageRepository contactMessageRepository;
    private final ContactMessageMapper contactMessageMapper;
    private final NotificationService notificationService;

    // ── Public ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ContactMessageResponseDTO saveMessage(ContactMessageRequestDTO dto) {
        ContactMessage message = contactMessageMapper.toEntity(dto);
        message.setLastName(emptyIfBlank(dto.getLastName()));
        message.setStatus(ContactMessageStatus.unread);
        message.setType(ContactType.CONTACT);
        return persistAndNotify(message);
    }

    @Override
    @Transactional
    public ContactMessageResponseDTO saveDiagnosticRequest(DiagnosticRequestDTO dto) {
        // Le message reste lisible tel quel dans la page admin « Messages » et les notifications
        String message = String.join("\n",
                "Fonction : " + dto.getRole().trim(),
                "Nombre de personnes à former : " + dto.getPeopleCount().getLabel(),
                "Besoin principal : " + dto.getNeed().getLabel(),
                "Contexte : " + orDash(dto.getContext()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", dto.getRole().trim());
        details.put("peopleCount", dto.getPeopleCount().name());
        details.put("need", dto.getNeed().name());
        details.put("context", trimToNull(dto.getContext()));

        ContactMessage entity = ContactMessage.builder()
                .firstName(dto.getFirstName().trim())
                .lastName(emptyIfBlank(dto.getLastName()))
                .email(dto.getEmail().trim())
                .phone(trimToNull(dto.getPhone()))
                .company(dto.getCompany().trim())
                .subject(DIAGNOSTIC_SUBJECT)
                .message(message)
                .status(ContactMessageStatus.unread)
                .type(ContactType.DIAGNOSTIC)
                .requesterType(RequesterType.ENTREPRISE)
                .details(details)
                .build();
        return persistAndNotify(entity);
    }

    @Override
    @Transactional
    public ContactMessageResponseDTO savePartnerApplication(PartnerApplicationRequestDTO dto) {
        String message = String.join("\n",
                "Organisme : " + orDash(dto.getOrganization()),
                "Domaine : " + dto.getDomain().getLabel(),
                "Profil LinkedIn : " + orDash(dto.getLinkedinUrl()),
                "Formation proposée : " + dto.getProposal().trim(),
                "Références : " + orDash(dto.getReferences()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("organization", trimToNull(dto.getOrganization()));
        details.put("domain", dto.getDomain().name());
        details.put("linkedinUrl", trimToNull(dto.getLinkedinUrl()));
        details.put("proposal", dto.getProposal().trim());
        details.put("references", trimToNull(dto.getReferences()));

        ContactMessage entity = ContactMessage.builder()
                .firstName(dto.getFirstName().trim())
                .lastName(emptyIfBlank(dto.getLastName()))
                .email(dto.getEmail().trim())
                .phone(trimToNull(dto.getPhone()))
                .company(trimToNull(dto.getOrganization()))
                .subject(PARTNER_APPLICATION_SUBJECT)
                .message(message)
                .status(ContactMessageStatus.unread)
                .type(ContactType.PARTNER_APPLICATION)
                .details(details)
                .build();
        return persistAndNotify(entity);
    }

    // ── Admin ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ContactMessageResponseDTO> getAllMessages(int page, int size) {
        return getAllMessages(page, size, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContactMessageResponseDTO> getAllMessages(int page, int size, ContactType type) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContactMessage> result = type == null
                ? contactMessageRepository.findAllByIsDeletedFalse(pageable)
                : contactMessageRepository.findAllByTypeAndIsDeletedFalse(type, pageable);
        return result.map(contactMessageMapper::toResponseDto);
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
        message.setLastName(emptyIfBlank(dto.getLastName()));
        message.setEmail(dto.getEmail());
        message.setCompany(dto.getCompany());
        message.setMessage(dto.getMessage());

        return contactMessageMapper.toResponseDto(contactMessageRepository.save(message));
    }

    // ── Privé ───────────────────────────────────────────────────────────────

    private ContactMessageResponseDTO persistAndNotify(ContactMessage message) {
        ContactMessage saved = contactMessageRepository.save(message);
        // Notification asynchrone — ne bloque pas la réponse API
        notificationService.notifyNewContactMessage(saved);
        return contactMessageMapper.toResponseDto(saved);
    }

    /** La colonne last_name est NOT NULL : un nom d'un seul mot est stocké avec un nom de famille vide. */
    private String emptyIfBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
