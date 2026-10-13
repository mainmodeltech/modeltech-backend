-- Profils et droits : un profil (rôle) devient un ensemble de droits modifiable par le SUPER_ADMIN ; un compte cumule
-- plusieurs profils. Les droits sont des chaînes du catalogue de l'application (enum Permission) ; le SUPER_ADMIN a tous les
-- droits sans qu'ils soient stockés (ceux ajoutés plus tard lui reviennent aussitôt).
--
-- Cette migration seme les profils livrés (voir DefaultProfiles.java : un test verifie que les deux ne divergent pas).
-- Les comptes existants ne sont PAS modifies : ils gardent leurs roles (ADMIN = « Gestion complete (transition) »,
-- EDITOR = « Editeur (ancien profil) ») ; la direction les reaffecte ensuite aux profils par metier.

ALTER TABLE roles ADD COLUMN label     VARCHAR(100);
ALTER TABLE roles ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT TRUE;   -- profil livre : non supprimable
ALTER TABLE roles ADD COLUMN is_staff  BOOLEAN NOT NULL DEFAULT TRUE;   -- profil du back-office (faux : apprenant)
UPDATE roles SET is_staff = FALSE WHERE name = 'ROLE_LEARNER';

CREATE TABLE role_permissions (
    role_id    UUID        NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(60) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- Nouveaux profils par metier
INSERT INTO roles (name, description) VALUES
    ('ROLE_FINANCE', 'Paiements, factures, remboursements, tarifs, parrainage, parts de revenu des partenaires.'),
    ('ROLE_ADMISSIONS', 'Candidatures, inscriptions, apprenants et messages entrants ; voit le statut des paiements sans pouvoir les modifier.'),
    ('ROLE_PEDAGOGY', 'Formations, programme, sessions, évaluations, certificats, suivi et communauté.'),
    ('ROLE_COMMUNICATION', 'Site, newsletter, témoignages, alumni, masterclass, messages et communauté.'),
    ('ROLE_AUDITOR', 'Voit les finances et le journal d''audit sans rien pouvoir modifier (expert-comptable, associé).')
ON CONFLICT (name) DO NOTHING;

-- Libelles et descriptions de tous les profils livres
UPDATE roles SET label = 'Direction', description = 'Tous les droits, y compris comptes, profils et journal d''audit. À réserver à deux personnes.' WHERE name = 'ROLE_SUPER_ADMIN';
UPDATE roles SET label = 'Finance', description = 'Paiements, factures, remboursements, tarifs, parrainage, parts de revenu des partenaires.' WHERE name = 'ROLE_FINANCE';
UPDATE roles SET label = 'Admissions', description = 'Candidatures, inscriptions, apprenants et messages entrants ; voit le statut des paiements sans pouvoir les modifier.' WHERE name = 'ROLE_ADMISSIONS';
UPDATE roles SET label = 'Pédagogie', description = 'Formations, programme, sessions, évaluations, certificats, suivi et communauté.' WHERE name = 'ROLE_PEDAGOGY';
UPDATE roles SET label = 'Communication', description = 'Site, newsletter, témoignages, alumni, masterclass, messages et communauté.' WHERE name = 'ROLE_COMMUNICATION';
UPDATE roles SET label = 'Formateur', description = 'Animation de ses sessions uniquement : appel, correction des projets, réponses aux questions, messages.' WHERE name = 'ROLE_TRAINER';
UPDATE roles SET label = 'Partenaire', description = 'Programme de ses formations uniquement.' WHERE name = 'ROLE_PARTNER';
UPDATE roles SET label = 'Lecture seule / Audit', description = 'Voit les finances et le journal d''audit sans rien pouvoir modifier (expert-comptable, associé).' WHERE name = 'ROLE_AUDITOR';
UPDATE roles SET label = 'Gestion complète (transition)', description = 'Ancien profil « Administrateur » : tout sauf les comptes, les profils et l''audit. À remplacer par les profils par métier.' WHERE name = 'ROLE_ADMIN';
UPDATE roles SET label = 'Éditeur (ancien profil)', description = 'Ancien profil « Éditeur de contenu » : catalogue, programmes, contenu du site, messages, newsletter.' WHERE name = 'ROLE_EDITOR';
UPDATE roles SET label = 'Apprenant' WHERE name = 'ROLE_LEARNER';

-- Droits de depart (aucune ligne pour ROLE_SUPER_ADMIN : tous les droits, implicitement)
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('DASHBOARD_FINANCE'), ('DASHBOARD_ACTIVITY'), ('APPLICATIONS_VIEW'), ('PRICE_OVERRIDE'), ('PAYMENTS_VIEW'), ('PAYMENTS_MANAGE'), ('PAYMENTS_REFUND'), ('INVOICES_VIEW'), ('INVOICES_MANAGE'), ('PROMO_VIEW'), ('PROMO_MANAGE'), ('PRICING_MANAGE'), ('PARTNERS_VIEW'), ('PARTNERS_REVENUE'), ('LEARNERS_VIEW'), ('CATALOG_VIEW'), ('CERTIFICATES_VIEW')) AS v(p)
 WHERE r.name = 'ROLE_FINANCE';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('DASHBOARD_ACTIVITY'), ('APPLICATIONS_VIEW'), ('APPLICATIONS_DECIDE'), ('ENROLLMENTS_MANAGE'), ('PAYMENTS_VIEW'), ('LEARNERS_VIEW'), ('LEARNERS_MANAGE'), ('CATALOG_VIEW'), ('SESSIONS_VIEW'), ('CERTIFICATES_VIEW'), ('MASTERCLASS_VIEW'), ('MESSAGES_MANAGE'), ('EMAIL_LOGS_VIEW')) AS v(p)
 WHERE r.name = 'ROLE_ADMISSIONS';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('DASHBOARD_ACTIVITY'), ('CATALOG_VIEW'), ('CATALOG_MANAGE'), ('PROGRAM_MANAGE'), ('SESSIONS_VIEW'), ('SESSIONS_TRACK'), ('SESSIONS_ASSIGN'), ('CERTIFICATES_VIEW'), ('CERTIFICATES_MANAGE'), ('CERTIFICATES_OVERRIDE'), ('SCOPE_ALL_SESSIONS'), ('SCOPE_ALL_FORMATIONS'), ('LEARNERS_VIEW'), ('PARTNERS_VIEW'), ('PARTNERS_MANAGE'), ('MASTERCLASS_VIEW'), ('COMMUNITY_MODERATE'), ('SITE_CONTENT_VIEW')) AS v(p)
 WHERE r.name = 'ROLE_PEDAGOGY';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('MASTERCLASS_VIEW'), ('MESSAGES_MANAGE'), ('NEWSLETTER_MANAGE'), ('COMMUNITY_MODERATE'), ('SITE_CONTENT_VIEW'), ('SITE_CONTENT_MANAGE')) AS v(p)
 WHERE r.name = 'ROLE_COMMUNICATION';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('SESSIONS_VIEW'), ('SESSIONS_TRACK')) AS v(p)
 WHERE r.name = 'ROLE_TRAINER';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('PROGRAM_MANAGE')) AS v(p)
 WHERE r.name = 'ROLE_PARTNER';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('DASHBOARD_FINANCE'), ('DASHBOARD_ACTIVITY'), ('APPLICATIONS_VIEW'), ('PAYMENTS_VIEW'), ('INVOICES_VIEW'), ('PROMO_VIEW'), ('PARTNERS_VIEW'), ('CATALOG_VIEW'), ('EMAIL_LOGS_VIEW'), ('AUDIT_VIEW')) AS v(p)
 WHERE r.name = 'ROLE_AUDITOR';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('DASHBOARD_FINANCE'), ('DASHBOARD_ACTIVITY'), ('APPLICATIONS_VIEW'), ('APPLICATIONS_DECIDE'), ('ENROLLMENTS_MANAGE'), ('PRICE_OVERRIDE'), ('PAYMENTS_VIEW'), ('PAYMENTS_MANAGE'), ('PAYMENTS_REFUND'), ('INVOICES_VIEW'), ('INVOICES_MANAGE'), ('PROMO_VIEW'), ('PROMO_MANAGE'), ('PRICING_MANAGE'), ('PARTNERS_VIEW'), ('PARTNERS_MANAGE'), ('PARTNERS_REVENUE'), ('LEARNERS_VIEW'), ('LEARNERS_MANAGE'), ('CATALOG_VIEW'), ('CATALOG_MANAGE'), ('PROGRAM_MANAGE'), ('SESSIONS_VIEW'), ('SESSIONS_TRACK'), ('SESSIONS_ASSIGN'), ('CERTIFICATES_VIEW'), ('CERTIFICATES_MANAGE'), ('CERTIFICATES_OVERRIDE'), ('SCOPE_ALL_SESSIONS'), ('SCOPE_ALL_FORMATIONS'), ('MASTERCLASS_VIEW'), ('MESSAGES_MANAGE'), ('NEWSLETTER_MANAGE'), ('COMMUNITY_MODERATE'), ('EMAIL_LOGS_VIEW'), ('SITE_CONTENT_VIEW'), ('SITE_CONTENT_MANAGE')) AS v(p)
 WHERE r.name = 'ROLE_ADMIN';
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('CATALOG_VIEW'), ('CATALOG_MANAGE'), ('PROGRAM_MANAGE'), ('SCOPE_ALL_FORMATIONS'), ('PARTNERS_VIEW'), ('PARTNERS_MANAGE'), ('MASTERCLASS_VIEW'), ('MESSAGES_MANAGE'), ('NEWSLETTER_MANAGE'), ('SITE_CONTENT_VIEW'), ('SITE_CONTENT_MANAGE')) AS v(p)
 WHERE r.name = 'ROLE_EDITOR';
