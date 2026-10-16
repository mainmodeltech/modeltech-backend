package com.modeltech.datamasteryhub.modules.auth.permission;

import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.security.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Profils et droits : lister, créer, modifier, rétablir et supprimer les profils du back-office. Réservé à la direction.
 *
 * <p>Garde-fous : le profil SUPER_ADMIN a tous les droits et n'est pas modifiable ; on ne peut accorder à un profil que des
 * droits qu'on détient soi-même (sauf SUPER_ADMIN) : aucune élévation de privilèges par ce chemin ; un profil livré ne
 * se supprime pas ; un profil encore attribué non plus.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final RoleRepository roleRepository;
    private final AdminUserRepository adminUserRepository;
    private final PermissionService permissionService;
    private final AuditService audit;

    public record ProfileView(UUID id, String name, String label, String description, boolean system, boolean allPermissions,
                              List<String> permissions, long activeUsers) {}

    public record PermissionView(String code, String group, String label) {}

    public record ProfileInput(String label, String description, Set<String> permissions) {}

    @Transactional(readOnly = true)
    public List<ProfileView> list() {
        return roleRepository.findAll().stream()
                .filter(r -> !r.isDeleted() && r.isStaff())
                .sorted(java.util.Comparator.comparing((Role r) -> !r.isSystem()).thenComparing(Role::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::view).toList();
    }

    public List<PermissionView> catalogue() {
        return java.util.Arrays.stream(Permission.values()).map(p -> new PermissionView(p.name(), p.group(), p.label())).toList();
    }

    @Transactional
    public ProfileView create(ProfileInput input) {
        String label = requireLabel(input.label());
        String name = "ROLE_" + slug(label);
        if (roleRepository.findByName(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un profil porte déjà ce nom.");
        }
        Set<String> permissions = checked(input.permissions());
        Role role = Role.builder().name(name).label(label).description(trim(input.description()))
                .system(false).staff(true).permissions(permissions).build();
        Role saved = roleRepository.save(role);
        audit.record(AuditAction.PROFILE_CHANGED, "Role", saved.getName(),
                "Profil « " + label + " » créé avec " + permissions.size() + " droit(s)");
        log.info("Profil créé : {} ({} droits)", name, permissions.size());
        return view(saved);
    }

    @Transactional
    public ProfileView update(UUID id, ProfileInput input) {
        Role role = get(id);
        requireNotSuperAdmin(role);
        if (input.label() != null) role.setLabel(requireLabel(input.label()));
        if (input.description() != null) role.setDescription(trim(input.description()));
        Set<String> before = new java.util.TreeSet<>(role.getPermissions());
        if (input.permissions() != null) {
            role.getPermissions().clear();
            role.getPermissions().addAll(checked(input.permissions()));
        }
        recordDiff(role, before, "modifié");
        log.info("Profil modifié : {} ({} droits)", role.getName(), role.getPermissions().size());
        return view(roleRepository.save(role));
    }

    /** Rétablit les droits (et le libellé) d'un profil livré. */
    @Transactional
    public ProfileView reset(UUID id) {
        Role role = get(id);
        requireNotSuperAdmin(role);
        DefaultProfiles.Profile defaults = DefaultProfiles.all().get(role.getName());
        if (defaults == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce profil n'a pas de réglage par défaut.");
        }
        Set<String> permissions = checked(defaults.permissions().stream().map(Enum::name).collect(Collectors.toSet()));
        Set<String> before = new java.util.TreeSet<>(role.getPermissions());
        role.setLabel(defaults.label());
        role.setDescription(defaults.description());
        role.getPermissions().clear();
        role.getPermissions().addAll(permissions);
        recordDiff(role, before, "rétabli aux droits par défaut");
        log.info("Profil rétabli aux droits par défaut : {}", role.getName());
        return view(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        Role role = get(id);
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un profil livré avec la plateforme ne se supprime pas (modifiez ses droits).");
        }
        long holders = adminUserRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && u.getRoles().stream().anyMatch(r -> r.getId().equals(role.getId()))).count();
        if (holders > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce profil est attribué à " + holders + " compte(s) : retirez-le d'abord.");
        }
        role.setDeleted(true);
        roleRepository.save(role);
        audit.record(AuditAction.PROFILE_CHANGED, "Role", role.getName(), "Profil « " + role.displayName() + " » supprimé");
        log.info("Profil supprimé : {}", role.getName());
    }

    /** Consigne les droits ajoutés et retirés d'un profil (la liste exacte, pour pouvoir répondre à « qui a donné ce droit ? »). */
    private void recordDiff(Role role, Set<String> before, String verb) {
        Set<String> added = new java.util.TreeSet<>(role.getPermissions());
        added.removeAll(before);
        Set<String> removed = new java.util.TreeSet<>(before);
        removed.removeAll(role.getPermissions());
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("droitsAjoutes", added);
        details.put("droitsRetires", removed);
        audit.record(AuditAction.PROFILE_CHANGED, "Role", role.getName(),
                "Profil « " + role.displayName() + " » " + verb + " : +" + added.size() + " / −" + removed.size() + " droit(s)",
                details, null);
    }

    // ── Règles partagées avec la gestion des comptes ────────────────

    /**
     * Un compte ne peut attribuer que des profils dont les droits sont inclus dans les siens (le SUPER_ADMIN n'est pas
     * limité), et seul un SUPER_ADMIN touche au profil SUPER_ADMIN.
     */
    public void requireCanAssign(Collection<Role> targetRoles) {
        if (Actor.isSuperAdmin()) return;
        Set<String> own = Actor.authorities();
        for (Role role : targetRoles) {
            if (RoleNames.SUPER_ADMIN.equals(role.getName())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seule la direction peut attribuer le profil « Direction ».");
            }
            if (!own.containsAll(permissionService.effective(List.of(role)))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous ne pouvez pas attribuer le profil « " + role.displayName() + " » : il donne des droits que vous n'avez pas.");
            }
        }
    }

    // ── privé ───────────────────────────────────────────────────────

    private Set<String> checked(Set<String> requested) {
        Set<String> permissions = new HashSet<>();
        if (requested != null) {
            for (String code : requested) {
                Permission permission = Permission.find(code)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Droit inconnu : " + code));
                permissions.add(permission.name());
            }
        }
        if (!Actor.isSuperAdmin() && !Actor.authorities().containsAll(permissions)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous ne pouvez accorder que des droits que vous avez vous-même.");
        }
        return permissions;
    }

    private Role get(UUID id) {
        return roleRepository.findById(id).filter(r -> !r.isDeleted() && r.isStaff())
                .orElseThrow(() -> new ResourceNotFoundException("Profil", "id", id));
    }

    private void requireNotSuperAdmin(Role role) {
        if (RoleNames.SUPER_ADMIN.equals(role.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le profil « Direction » a tous les droits : il ne se modifie pas.");
        }
    }

    private ProfileView view(Role role) {
        boolean all = RoleNames.SUPER_ADMIN.equals(role.getName());
        List<String> permissions = all ? Permission.names()
                : role.getPermissions().stream().filter(p -> Permission.find(p).isPresent()).sorted().toList();
        long users = adminUserRepository.countByRolesNameAndActiveTrueAndIsDeletedFalse(role.getName());
        return new ProfileView(role.getId(), role.getName(), role.displayName(), role.getDescription(), role.isSystem(), all, permissions, users);
    }

    private static String requireLabel(String label) {
        String trimmed = trim(label);
        if (trimmed == null || trimmed.length() < 3 || trimmed.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom du profil doit contenir entre 3 et 100 caractères.");
        }
        return trimmed;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** « Responsable Admissions » → RESPONSABLE_ADMISSIONS (50 caractères au plus avec le préfixe). */
    private static String slug(String label) {
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom du profil doit contenir des lettres ou des chiffres.");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }
}
