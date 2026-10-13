package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.security.AppUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Le second facteur est obligatoire pour tout compte du personnel ({@code app.auth.mfa.enforced}, vrai par défaut : ne
 * le désactiver qu'en développement local ou en test). Les apprenants n'y sont pas soumis.
 */
@Component
public class MfaPolicy {

    @Value("${app.auth.mfa.enforced:true}")
    private boolean enforced;

    public boolean enforcedFor(AppUserDetails user) {
        return enforced && user.isStaff();
    }

    public boolean enforced() {
        return enforced;
    }
}
