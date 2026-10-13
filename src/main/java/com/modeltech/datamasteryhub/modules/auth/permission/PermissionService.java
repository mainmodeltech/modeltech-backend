package com.modeltech.datamasteryhub.modules.auth.permission;

import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Droits effectifs d'un compte : union des droits de ses profils ; le SUPER_ADMIN a tous les droits du catalogue. */
@Component
public class PermissionService {

    public Set<String> effective(Collection<Role> roles) {
        Set<String> all = new LinkedHashSet<>();
        for (Role role : roles) {
            if (RoleNames.SUPER_ADMIN.equals(role.getName())) return new LinkedHashSet<>(Permission.names());
            // Un droit inconnu du catalogue (retiré du code depuis) ne donne rien
            role.getPermissions().stream().filter(p -> Permission.find(p).isPresent()).sorted().forEach(all::add);
        }
        return all;
    }
}
