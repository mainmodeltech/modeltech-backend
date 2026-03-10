-- ============================================================
-- V2 : RBAC + Sécurité Auth
-- - Table roles
-- - Table admin_user_roles (jointure)
-- - Table password_reset_tokens (forgot password)
-- - Table token_blacklist (logout / révocation JWT)
-- ============================================================

-- 1. TABLE ROLES
CREATE TABLE IF NOT EXISTS roles (
                                     id   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
                                     name VARCHAR(50) NOT NULL UNIQUE,   -- ex: ROLE_SUPER_ADMIN, ROLE_ADMIN, ROLE_EDITOR
                                     description VARCHAR(255),
                                     created_by       VARCHAR(255),
                                     updated_by       VARCHAR(255),
                                     deleted_by       VARCHAR(255),
                                     created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                                     updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                                     deleted_at  TIMESTAMP,
                                     is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE
);

-- 2. TABLE DE JOINTURE admin_user_roles
CREATE TABLE IF NOT EXISTS admin_user_roles (
                                                admin_user_id UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
                                                role_id       UUID NOT NULL REFERENCES roles(id)       ON DELETE CASCADE,
                                                assigned_at   TIMESTAMP NOT NULL DEFAULT NOW(),
                                                assigned_by   VARCHAR(255),
                                                PRIMARY KEY (admin_user_id, role_id)
);

-- 3. TABLE password_reset_tokens
CREATE TABLE IF NOT EXISTS password_reset_tokens (
                                                     id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
                                                     token       VARCHAR(255) NOT NULL UNIQUE,
                                                     email       VARCHAR(255) NOT NULL,
                                                     expires_at  TIMESTAMP   NOT NULL,
                                                     used        BOOLEAN     NOT NULL DEFAULT FALSE,
                                                     used_at     TIMESTAMP,
                                                     created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_prt_token ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_prt_email ON password_reset_tokens(email);

-- 4. TABLE token_blacklist (pour invalidation JWT au logout)
CREATE TABLE IF NOT EXISTS token_blacklist (
                                               id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
                                               token_hash  VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 du token (évite de stocker le token brut)
                                               email       VARCHAR(255) NOT NULL,
                                               expires_at  TIMESTAMP   NOT NULL,          -- même expiration que le JWT
                                               blacklisted_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tb_token_hash  ON token_blacklist(token_hash);
CREATE INDEX IF NOT EXISTS idx_tb_expires_at  ON token_blacklist(expires_at);

-- 5. DONNÉES INITIALES : rôles de base
INSERT INTO roles (name, description) VALUES
                                          ('ROLE_SUPER_ADMIN', 'Accès total à toutes les fonctionnalités'),
                                          ('ROLE_ADMIN',       'Accès standard au backoffice'),
                                          ('ROLE_EDITOR',      'Gestion du contenu uniquement')
ON CONFLICT (name) DO NOTHING;