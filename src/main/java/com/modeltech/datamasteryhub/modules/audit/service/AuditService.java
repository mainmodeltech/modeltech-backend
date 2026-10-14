package com.modeltech.datamasteryhub.modules.audit.service;

import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.audit.entity.AuditEntry;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Écrit dans le journal d'audit, <b>dans la transaction de l'action</b> : si l'action échoue et est annulée, sa trace
 * l'est aussi ; si la trace ne peut pas être écrite, l'action n'a pas lieu. L'auteur, ses profils et son adresse IP sont
 * relevés ici, jamais passés par l'appelant, pour qu'on ne puisse ni les oublier ni les falsifier.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String SYSTEM = "système";

    private final AuditRepository repository;
    private final AdminUserRepository adminUsers;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entityType, Object entityId, String summary) {
        record(action, entityType, entityId, summary, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entityType, Object entityId, String summary,
                       Map<String, Object> details, AuditFlag flag) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean known = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
        String email = known ? auth.getName() : SYSTEM;

        AuditEntry e = new AuditEntry();
        e.setActorEmail(email);
        e.setActorName(known ? adminUsers.findByEmailAndIsDeletedFalse(email).map(u -> u.getFullName()).orElse(null) : null);
        e.setActorProfiles(known ? auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).sorted().collect(Collectors.joining(", ")) : null);
        e.setAction(action.name());
        e.setCategory(action.category());
        e.setEntityType(entityType);
        e.setEntityId(entityId == null ? null : String.valueOf(entityId));
        e.setSummary(summary);
        e.setDetails(details == null || details.isEmpty() ? null : details);
        e.setFlag(flag == null ? null : flag.name());
        e.setIp(currentIp());
        repository.save(e);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return IpRateLimiter.clientIp(attrs.getRequest());
        }
        return null;
    }
}
