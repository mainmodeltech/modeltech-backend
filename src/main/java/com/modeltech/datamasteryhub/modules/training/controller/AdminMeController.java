package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * « Mon espace » du back-office : ce que le compte connecté peut ouvrir.
 * <ul>
 *   <li>sessions : administration = toutes ; formateur = celles qui lui sont confiées ; autres = aucune ;</li>
 *   <li>formations : administration et éditeur = toutes ; partenaire = les siennes ; formateur = celles de ses sessions.</li>
 * </ul>
 * Sert de point d'entrée aux formateurs et partenaires, qui n'ont pas accès aux listes générales.
 */
@RestController
@RequestMapping("/api/v1/admin/me")
@RequiredArgsConstructor
@Tag(name = "Admin - Mon espace")
public class AdminMeController {

    private final BootcampSessionRepository sessionRepository;
    private final BootcampRepository bootcampRepository;
    private final AdminUserRepository adminUserRepository;

    public record MySession(UUID sessionId, String sessionName, UUID formationId, String formationTitle,
                            LocalDate startDate, LocalDate endDate, String status,
                            Integer capacity, Integer participants, String trainerName) {}

    public record MyFormation(UUID id, String title, String slug, boolean published, String domainName,
                              String deliveredBy, String partnerName, long sessionCount) {}

    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<MySession>>> sessions(Authentication auth) {
        List<MySession> list = visibleSessions(auth).stream().map(this::toSession).toList();
        return ResponseEntity.ok(ApiResponse.ok(list.size() + " session(s)", list));
    }

    @GetMapping("/formations")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<MyFormation>>> formations(Authentication auth) {
        Collection<String> roles = roles(auth);
        List<Bootcamp> formations;
        if (roles.contains(Permission.SCOPE_ALL_FORMATIONS.name())) {
            formations = bootcampRepository.findAllByIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc();
        } else if (roles.contains(RoleNames.PARTNER)) {
            UUID partnerId = adminUserRepository.findByEmailAndIsDeletedFalse(auth.getName())
                    .map(AdminUser::getPartner).map(p -> p.getId()).orElse(null);
            formations = partnerId == null ? List.of()
                    : bootcampRepository.findAllByPartnerIdAndIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc(partnerId);
        } else {
            // Formations des sessions confiées à la personne (formateur ou toute autre personne désignée sur une session)
            Map<UUID, Bootcamp> byId = new LinkedHashMap<>();
            visibleSessions(auth).forEach(s -> byId.putIfAbsent(s.getBootcamp().getId(), s.getBootcamp()));
            formations = List.copyOf(byId.values());
        }
        List<MyFormation> list = formations.stream().map(this::toFormation).toList();
        return ResponseEntity.ok(ApiResponse.ok(list.size() + " formation(s)", list));
    }

    private List<BootcampSession> visibleSessions(Authentication auth) {
        Collection<String> roles = roles(auth);
        if (roles.contains(Permission.SCOPE_ALL_SESSIONS.name())) {
            return sessionRepository.findAllByIsDeletedFalseOrderByStartDateDesc();
        }
        return sessionRepository.findAllByTrainerEmailIgnoreCaseAndIsDeletedFalseOrderByStartDateDesc(auth.getName());
    }

    private Collection<String> roles(Authentication auth) {
        // Les noms portent le préfixe ROLE_, comme les constantes de RoleNames
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private MySession toSession(BootcampSession s) {
        return new MySession(s.getId(), s.getSessionName(), s.getBootcamp().getId(), s.getBootcamp().getTitle(),
                s.getStartDate(), s.getEndDate(), String.valueOf(s.getStatus()),
                s.getMaxParticipants(), s.getCurrentParticipants(),
                s.getTrainer() != null ? s.getTrainer().getFullName() : null);
    }

    private MyFormation toFormation(Bootcamp b) {
        long sessions = sessionRepository.findAllByBootcampIdAndIsDeletedFalseOrderByStartDateAsc(b.getId()).size();
        return new MyFormation(b.getId(), b.getTitle(), b.getSlug(), Boolean.TRUE.equals(b.getPublished()),
                b.getDomain() != null ? b.getDomain().getName() : null,
                String.valueOf(b.getDeliveredBy()),
                b.getPartner() != null ? b.getPartner().getName() : null, sessions);
    }
}
