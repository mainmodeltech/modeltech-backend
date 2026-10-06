package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
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
import java.util.Set;

/**
 * Charge un compte par e-mail : comptes de back-office ({@code admin_users}) d'abord,
 * puis apprenants ({@code learners}). L'unicité d'e-mail entre les deux tables est garantie
 * à la création des comptes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    /** Mot de passe inutilisable (n'est pas un hash BCrypt valide) : compte sans mot de passe défini. */
    private static final String NO_PASSWORD = "!";

    private final AdminUserRepository adminUserRepository;
    private final LearnerRepository learnerRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return adminUserRepository.findByEmailAndIsDeletedFalse(email)
                .map(this::toUserDetails)
                .or(() -> learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).map(this::toUserDetails))
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + email));
    }

    private UserDetails toUserDetails(AdminUser admin) {
        return User.builder()
                .username(admin.getEmail())
                .password(admin.getPasswordHash())
                .authorities(authorities(admin.getRoles()))
                .accountLocked(!admin.isActive())
                .build();
    }

    private UserDetails toUserDetails(Learner learner) {
        return User.builder()
                .username(learner.getEmail())
                .password(learner.getPasswordHash() != null ? learner.getPasswordHash() : NO_PASSWORD)
                .authorities(authorities(learner.getRoles()))
                .accountLocked(!learner.isActive())
                .build();
    }

    private List<SimpleGrantedAuthority> authorities(Set<Role> roles) {
        return roles.stream().map(role -> new SimpleGrantedAuthority(role.getName())).toList();
    }
}
