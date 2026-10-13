package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.modules.auth.permission.PermissionService;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.security.AppUserDetails;
import com.modeltech.datamasteryhub.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
    private final PermissionService permissionService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return adminUserRepository.findByEmailAndIsDeletedFalse(email)
                .map(this::toUserDetails)
                .or(() -> learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email).map(this::toUserDetails))
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + email));
    }

    private UserDetails toUserDetails(AdminUser admin) {
        return new AppUserDetails(admin.getEmail(), admin.getPasswordHash(), admin.isActive(), staffAuthorities(admin.getRoles()),
                admin.getId(), admin.getFullName(), JwtTokenProvider.USER_TYPE_ADMIN, admin.getSecurityVersion(), admin.isMfaEnabled());
    }

    private UserDetails toUserDetails(Learner learner) {
        return new AppUserDetails(learner.getEmail(), learner.getPasswordHash() != null ? learner.getPasswordHash() : NO_PASSWORD,
                learner.isActive(), authorities(learner.getRoles()),
                learner.getId(), learner.getFullName(), JwtTokenProvider.USER_TYPE_LEARNER, learner.getSecurityVersion(), false);
    }

    /** Profils (ROLE_*), droits qu'ils portent (PAYMENTS_MANAGE…) et accès au back-office : tout ce que les règles d'accès testent. */
    private List<SimpleGrantedAuthority> staffAuthorities(Set<Role> roles) {
        List<SimpleGrantedAuthority> all = new java.util.ArrayList<>(authorities(roles));
        permissionService.effective(roles).forEach(p -> all.add(new SimpleGrantedAuthority(p)));
        all.add(new SimpleGrantedAuthority(Permission.BACKOFFICE_ACCESS));
        return all;
    }

    private List<SimpleGrantedAuthority> authorities(Set<Role> roles) {
        return roles.stream().map(role -> new SimpleGrantedAuthority(role.getName())).toList();
    }
}
