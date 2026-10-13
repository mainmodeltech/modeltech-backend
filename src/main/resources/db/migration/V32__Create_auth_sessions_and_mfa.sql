-- Sessions de connexion (jeton de rafraichissement) et authentification a deux facteurs du personnel.

-- Version de securite : elle est copiee dans chaque jeton d'acces ; l'incrementer (changement de mot de
-- passe, de role, desactivation, reinitialisation du MFA) invalide d'un coup tous les jetons deja emis.
ALTER TABLE admin_users ADD COLUMN security_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE learners    ADD COLUMN security_version INTEGER NOT NULL DEFAULT 0;

-- MFA (TOTP) : secret chiffre ; le MFA n'est actif qu'une fois « mfa_enabled_at » renseigne (enrolement confirme).
ALTER TABLE admin_users ADD COLUMN mfa_secret_enc       TEXT;
ALTER TABLE admin_users ADD COLUMN mfa_enabled_at       TIMESTAMP;
ALTER TABLE admin_users ADD COLUMN mfa_last_step        BIGINT;
ALTER TABLE admin_users ADD COLUMN mfa_failed_attempts  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE admin_users ADD COLUMN mfa_locked_until     TIMESTAMP;

-- Codes de secours a usage unique (empreinte seulement).
CREATE TABLE admin_recovery_codes (
    id            UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    admin_user_id UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    code_hash     VARCHAR(64) NOT NULL,
    used_at       TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recovery_code UNIQUE (admin_user_id, code_hash)
);
CREATE INDEX idx_recovery_codes_user ON admin_recovery_codes (admin_user_id);

-- Une ligne = une session ouverte sur un appareil. Le jeton de rafraichissement n'est jamais stocke en clair.
CREATE TABLE auth_sessions (
    id                  UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    subject_type        VARCHAR(10) NOT NULL CHECK (subject_type IN ('ADMIN', 'LEARNER')),
    subject_id          UUID NOT NULL,
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    previous_token_hash VARCHAR(64),
    rotated_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP NOT NULL,
    max_expires_at      TIMESTAMP NOT NULL,
    revoked_at          TIMESTAMP,
    mfa_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    ip                  VARCHAR(64),
    user_agent          VARCHAR(200)
);
CREATE INDEX idx_auth_sessions_subject ON auth_sessions (subject_type, subject_id);
CREATE INDEX idx_auth_sessions_prev    ON auth_sessions (previous_token_hash);
CREATE INDEX idx_auth_sessions_expiry  ON auth_sessions (expires_at);
