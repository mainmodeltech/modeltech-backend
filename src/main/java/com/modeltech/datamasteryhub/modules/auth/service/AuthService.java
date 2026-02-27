package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse.AdminUserResponse me(String email);

    void changePassword(String email, ChangePasswordRequest request);
}