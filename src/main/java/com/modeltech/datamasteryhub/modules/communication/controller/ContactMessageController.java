package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contact-messages")
@RequiredArgsConstructor
public class ContactMessageController {

    private final ContactMessageService contactMessageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactMessageResponseDTO create(@Valid @RequestBody ContactMessageRequestDTO dto) {
        return contactMessageService.saveMessage(dto);
    }
}