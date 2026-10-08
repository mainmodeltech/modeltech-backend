package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthOptionsResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;

public interface GoogleLoginService {

    AuthOptionsResponse options();

    /** 404 si Google n'est pas configuré ; 401 si le jeton est refusé ou si aucun compte actif ne correspond. */
    AuthResponse login(String credential);
}
