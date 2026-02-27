package com.modeltech.datamasteryhub.security;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AdminUser adminUser = adminUserRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> {
                    log.error("Utilisateur introuvable: {}", email);
                    return new UsernameNotFoundException("Utilisateur introuvable: " + email);
                });

        if (!adminUser.isActive()) {
            throw new UsernameNotFoundException("Compte désactivé: " + email);
        }

        return User.builder()
                .username(adminUser.getEmail())
                .password(adminUser.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + adminUser.getRole())))
                .build();
    }
}