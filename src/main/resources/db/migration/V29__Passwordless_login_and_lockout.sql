-- Connexion sans mot de passe (lien + code a 6 chiffres envoyes par message) et verrouillage temporaire apres echecs.

CREATE TABLE login_challenges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    token_hash      VARCHAR(64)  NOT NULL,   -- SHA-256 du jeton du lien (le jeton lui-meme n'est jamais stocke)
    code_hash       VARCHAR(64)  NOT NULL,   -- SHA-256 de (e-mail + code)
    expires_at      TIMESTAMP    NOT NULL,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    consumed        BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_login_challenges_token UNIQUE (token_hash)
);

CREATE INDEX idx_login_challenges_email ON login_challenges (email, created_at DESC);

ALTER TABLE admin_users ADD COLUMN failed_login_attempts INTEGER   NOT NULL DEFAULT 0;
ALTER TABLE admin_users ADD COLUMN locked_until          TIMESTAMP;

ALTER TABLE learners    ADD COLUMN failed_login_attempts INTEGER   NOT NULL DEFAULT 0;
ALTER TABLE learners    ADD COLUMN locked_until          TIMESTAMP;
