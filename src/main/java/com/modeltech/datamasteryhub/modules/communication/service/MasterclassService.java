package com.modeltech.datamasteryhub.modules.communication.service;

import com.modeltech.datamasteryhub.modules.communication.dto.request.MasterclassRegistrationRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.MasterclassRegistrationResponseDTO;
import org.springframework.data.domain.Page;

public interface MasterclassService {

    MasterclassRegistrationResponseDTO register(MasterclassRegistrationRequestDTO req);
    Page<MasterclassRegistrationResponseDTO> getAll(String masterclassId, int page, int size);
    long count(String masterclassId);


}
