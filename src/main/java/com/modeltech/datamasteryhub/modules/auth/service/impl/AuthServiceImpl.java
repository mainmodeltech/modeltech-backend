package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AdminUserRepository adminUserRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Tentative de connexion: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        String token = jwtTokenProvider.generateToken(authentication);

        // Mise à jour de la dernière connexion
        AdminUser adminUser = adminUserRepository
                .findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "email", request.getEmail()));
        adminUser.setLastLoginAt(LocalDateTime.now());
        adminUserRepository.save(adminUser);

        log.info("Connexion réussie: {}", request.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .user(AuthResponse.AdminUserResponse.builder()
                        .id(adminUser.getId())
                        .email(adminUser.getEmail())
                        .fullName(adminUser.getFullName())
                        .role(adminUser.getRole())
                        .build())
                .build();
    }

    @Override
    public AuthResponse.AdminUserResponse me(String email) {
        AdminUser adminUser = adminUserRepository
                .findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "email", email));

        return AuthResponse.AdminUserResponse.builder()
                .id(adminUser.getId())
                .email(adminUser.getEmail())
                .fullName(adminUser.getFullName())
                .role(adminUser.getRole())
                .build();
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        AdminUser adminUser = adminUserRepository
                .findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "email", email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), adminUser.getPasswordHash())) {
            throw new BadCredentialsException("Mot de passe actuel incorrect");
        }

        adminUser.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminUserRepository.save(adminUser);
        log.info("Mot de passe modifié pour: {}", email);
    }
}