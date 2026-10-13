package com.modeltech.datamasteryhub.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

/**
 * Compte authentifié : en plus des droits, la version de sécurité (voir {@link JwtTokenProvider}), le type de
 * compte et l'état du second facteur, lus en base à chaque requête.
 */
public class AppUserDetails extends User {

    private final UUID id;
    private final String fullName;
    private final String userType;
    private final int securityVersion;
    private final boolean mfaEnabled;

    public AppUserDetails(String username, String password, boolean accountNonLocked,
                          Collection<? extends GrantedAuthority> authorities,
                          UUID id, String fullName, String userType, int securityVersion, boolean mfaEnabled) {
        super(username, password, true, true, true, accountNonLocked, authorities);
        this.id = id;
        this.fullName = fullName;
        this.userType = userType;
        this.securityVersion = securityVersion;
        this.mfaEnabled = mfaEnabled;
    }

    public UUID id() { return id; }
    public String fullName() { return fullName; }
    /** {@link JwtTokenProvider#USER_TYPE_ADMIN} (back-office) ou {@link JwtTokenProvider#USER_TYPE_LEARNER}. */
    public String userType() { return userType; }
    public int securityVersion() { return securityVersion; }
    public boolean mfaEnabled() { return mfaEnabled; }

    public boolean isStaff() {
        return JwtTokenProvider.USER_TYPE_ADMIN.equals(userType);
    }
}
