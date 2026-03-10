package com.modeltech.datamasteryhub.modules.auth.service.impl;

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

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AdminUser adminUser = adminUserRepository
                .findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur non trouvé : " + email));

        // AVANT (cassé) : List.of(new SimpleGrantedAuthority(adminUser.getRole()))
        // APRÈS (RBAC)  : on mappe le Set<Role> vers des GrantedAuthority
        var authorities = adminUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());

        return User.builder()
                .username(adminUser.getEmail())
                .password(adminUser.getPasswordHash())
                .authorities(authorities)
                .accountLocked(!adminUser.isActive())
                .build();
    }
}