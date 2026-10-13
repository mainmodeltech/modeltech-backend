package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.AuthSession;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Événements de sécurité d'un compte (changement de mot de passe, de rôle, désactivation, réinitialisation du MFA) :
 * la version de sécurité augmente, ce qui invalide d'un coup tous les jetons d'accès déjà émis, et toutes les
 * sessions (jetons de rafraîchissement) sont terminées. La personne doit se reconnecter.
 */
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private final AdminUserRepository adminUserRepository;
    private final LearnerRepository learnerRepository;
    private final AuthSessionService sessions;

    @Transactional
    public void invalidateSessions(AdminUser admin) {
        admin.setSecurityVersion(admin.getSecurityVersion() + 1);
        adminUserRepository.save(admin);
        sessions.revokeAllFor(AuthSession.ADMIN, admin.getId());
    }

    @Transactional
    public void invalidateSessions(Learner learner) {
        learner.setSecurityVersion(learner.getSecurityVersion() + 1);
        learnerRepository.save(learner);
        sessions.revokeAllFor(AuthSession.LEARNER, learner.getId());
    }
}
