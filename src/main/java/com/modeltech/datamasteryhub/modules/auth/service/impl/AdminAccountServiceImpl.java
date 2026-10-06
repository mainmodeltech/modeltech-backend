package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.modeltech.datamasteryhub.common.util.TokenUtils;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.dto.request.CreateAdminUserRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.UpdateAdminUserRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AdminUserSummaryResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.auth.service.AdminAccountService;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final int INVITATION_VALID_HOURS = LearnerServiceImpl.INVITATION_VALID_HOURS;

    private final AdminUserRepository adminUserRepository;
    private final LearnerRepository learnerRepository;
    private final RoleRepository roleRepository;
    private final PartnerRepository partnerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Override
    public Page<AdminUserSummaryResponse> findAll(Pageable pageable) {
        return adminUserRepository.findAllByIsDeletedFalse(pageable).map(this::toResponse);
    }

    @Override
    public AdminUserSummaryResponse findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public AdminUserSummaryResponse create(CreateAdminUserRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (adminUserRepository.existsByEmailAndIsDeletedFalse(email)
                || learnerRepository.existsByEmailIgnoreCaseAndIsDeletedFalse(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet e-mail.");
        }

        Set<Role> roles = resolveRoles(request.getRoles());
        AdminUser admin = new AdminUser();
        admin.setEmail(email);
        admin.setFullName(request.getFullName().trim());
        // Mot de passe aléatoire inconnu de tous : le compte ne s'ouvre qu'avec le lien d'invitation
        admin.setPasswordHash(passwordEncoder.encode(TokenUtils.randomToken()));
        admin.setRoles(roles);
        admin.setPartner(resolvePartner(roles, request.getPartnerId(), null));

        AdminUser saved = adminUserRepository.save(admin);
        String token = authService.createPasswordResetToken(email, INVITATION_VALID_HOURS * 60);
        notificationService.sendAccountInvitationEmail(email, saved.getFullName(),
                authService.passwordSetupLink(token, false), INVITATION_VALID_HOURS, false);
        log.info("Compte de back-office créé : {} ({})", email, roleNames(roles));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AdminUserSummaryResponse update(UUID id, UpdateAdminUserRequest request, String actingEmail) {
        AdminUser admin = getOrThrow(id);
        boolean self = admin.getEmail().equalsIgnoreCase(actingEmail);
        boolean wasActiveSuperAdmin = admin.isActive() && admin.hasRole(RoleNames.SUPER_ADMIN);

        Set<Role> newRoles = request.getRoles() != null ? resolveRoles(request.getRoles()) : admin.getRoles();
        boolean newActive = request.getActive() != null ? request.getActive() : admin.isActive();
        boolean willBeActiveSuperAdmin = newActive
                && newRoles.stream().anyMatch(r -> RoleNames.SUPER_ADMIN.equals(r.getName()));

        if (self && !newActive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous ne pouvez pas désactiver votre propre compte.");
        }
        if (self && wasActiveSuperAdmin && !willBeActiveSuperAdmin) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous ne pouvez pas retirer votre propre rôle SUPER_ADMIN.");
        }
        if (wasActiveSuperAdmin && !willBeActiveSuperAdmin
                && adminUserRepository.countByRolesNameAndActiveTrueAndIsDeletedFalse(RoleNames.SUPER_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La plateforme doit conserver au moins un SUPER_ADMIN actif.");
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            admin.setFullName(request.getFullName().trim());
        }
        admin.setRoles(newRoles);
        admin.setActive(newActive);
        admin.setPartner(resolvePartner(newRoles, request.getPartnerId(), admin.getPartner()));

        return toResponse(adminUserRepository.save(admin));
    }

    // ── Privé ───────────────────────────────────────────────────────

    private AdminUser getOrThrow(UUID id) {
        return adminUserRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte", "id", id));
    }

    /** Rôles demandés → entités ; seuls les rôles de back-office sont acceptés. */
    private Set<Role> resolveRoles(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Au moins un rôle est obligatoire.");
        }
        Set<Role> roles = new HashSet<>();
        for (String name : requested) {
            String normalized = RoleNames.normalize(name);
            if (!RoleNames.STAFF.contains(normalized)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Rôle invalide pour un compte de back-office : " + name);
            }
            roles.add(roleRepository.findByName(normalized)
                    .orElseThrow(() -> new IllegalStateException("Rôle " + normalized + " absent (migration V20 ?)")));
        }
        return roles;
    }

    /** PARTNER ⇒ partenaire obligatoire (celui fourni, sinon l'actuel) ; autres rôles ⇒ aucun partenaire. */
    private Partner resolvePartner(Set<Role> roles, UUID requestedPartnerId, Partner current) {
        boolean isPartner = roles.stream().anyMatch(r -> RoleNames.PARTNER.equals(r.getName()));
        if (!isPartner) return null;

        Partner partner = current;
        if (requestedPartnerId != null) {
            partner = partnerRepository.findByIdAndIsDeletedFalse(requestedPartnerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Partenaire", "id", requestedPartnerId));
        }
        if (partner == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un compte PARTNER doit être rattaché à un partenaire (partnerId).");
        }
        return partner;
    }

    private String roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).sorted().collect(Collectors.joining(", "));
    }

    private AdminUserSummaryResponse toResponse(AdminUser a) {
        return AdminUserSummaryResponse.builder()
                .id(a.getId())
                .email(a.getEmail())
                .fullName(a.getFullName())
                .roles(a.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(java.util.TreeSet::new)))
                .active(a.isActive())
                .partnerId(a.getPartner() != null ? a.getPartner().getId() : null)
                .lastLoginAt(a.getLastLoginAt())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
