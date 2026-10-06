-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "learner-accounts" : comptes apprenants, nouveaux rôles, rattrapage des
-- rôles des admins existants (prérequis du contrôle de rôle sur /api/v1/admin/**).
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Nouveaux rôles (V13 en avait créé trois : SUPER_ADMIN, ADMIN, EDITOR)
INSERT INTO roles (name, description) VALUES
    ('ROLE_LEARNER', 'Apprenant : accès à son espace de formation'),
    ('ROLE_TRAINER', 'Formateur : gestion de ses sessions et de ses apprenants'),
    ('ROLE_PARTNER', 'Partenaire formateur : gestion de ses propres formations')
ON CONFLICT (name) DO NOTHING;

-- 2. Un compte PARTNER (admin_users) est rattaché à son partenaire
ALTER TABLE admin_users
    ADD COLUMN IF NOT EXISTS partner_id UUID REFERENCES partners(id);

-- 3. Apprenants : compte distinct des admins. password_hash reste nul tant que
--    l'apprenant n'a pas défini son mot de passe via le lien reçu par e-mail.
CREATE TABLE IF NOT EXISTS learners (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,
    password_hash     TEXT,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL DEFAULT '',
    phone             VARCHAR(50),
    country           VARCHAR(100),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified_at TIMESTAMP,
    last_login_at     TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_learners_email_lower ON learners (lower(email));

CREATE TABLE IF NOT EXISTS learner_roles (
    learner_id  UUID      NOT NULL REFERENCES learners(id) ON DELETE CASCADE,
    role_id     UUID      NOT NULL REFERENCES roles(id)    ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    assigned_by VARCHAR(255),
    PRIMARY KEY (learner_id, role_id)
);

-- 4. RATTRAPAGE DES ADMINS EXISTANTS
-- V13 a créé admin_user_roles sans la remplir : les rôles des admins existants ont
-- été posés à la main (ou jamais). Tant que /admin/** ne vérifiait que « authentifié »,
-- cela n'avait pas d'importance ; le contrôle de rôle l'exigera.
-- 4a. Rôle déduit de l'ancienne colonne admin_users.role (ex. 'ADMIN' → ROLE_ADMIN)
INSERT INTO admin_user_roles (admin_user_id, role_id, assigned_by)
SELECT u.id, r.id, 'migration V20'
FROM admin_users u
JOIN roles r ON r.name = 'ROLE_' || upper(u.role)
WHERE NOT EXISTS (SELECT 1 FROM admin_user_roles x WHERE x.admin_user_id = u.id);

-- 4b. Tout admin encore sans rôle garde son niveau d'accès actuel (accès complet au
--     backoffice) : ROLE_ADMIN. Aucun compte existant n'est donc verrouillé.
INSERT INTO admin_user_roles (admin_user_id, role_id, assigned_by)
SELECT u.id, r.id, 'migration V20'
FROM admin_users u
CROSS JOIN roles r
WHERE r.name = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM admin_user_roles x WHERE x.admin_user_id = u.id);

-- 4c. Amorçage : la gestion des comptes (/api/v1/admin/users) exige ROLE_SUPER_ADMIN. Si aucun
--     admin actif ne l'a, le plus ancien compte actif le reçoit — sinon personne ne pourrait
--     jamais créer ni promouvoir un compte. À revoir ensuite depuis le back-office.
INSERT INTO admin_user_roles (admin_user_id, role_id, assigned_by)
SELECT u.id, r.id, 'migration V20 (amorçage)'
FROM (
    SELECT id FROM admin_users WHERE is_deleted = FALSE AND active = TRUE
    ORDER BY created_at, id LIMIT 1
) u
CROSS JOIN roles r
WHERE r.name = 'ROLE_SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM admin_user_roles x JOIN roles xr ON xr.id = x.role_id
      JOIN admin_users xu ON xu.id = x.admin_user_id
      WHERE xr.name = 'ROLE_SUPER_ADMIN' AND xu.is_deleted = FALSE AND xu.active = TRUE);
