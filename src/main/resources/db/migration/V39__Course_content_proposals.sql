-- Propositions de modification du programme : un formateur propose des changements sur le programme d'une formation dont
-- une session lui est confiée ; la pédagogie valide (fusion dans le programme en ligne), demande des modifications ou refuse.
-- Le contenu proposé est gardé tel quel ({content, quizBanks}) : rien n'est écrit dans le programme avant la validation.

CREATE TABLE course_content_proposals (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    bootcamp_id              UUID         NOT NULL REFERENCES bootcamps(id),
    author_id                UUID         NOT NULL REFERENCES admin_users(id),
    status                   VARCHAR(20)  NOT NULL
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')),
    payload                  JSONB        NOT NULL,
    -- Date du programme en ligne au début de la proposition : s'il a changé depuis, la proposition est « à revoir »
    base_content_updated_at  TIMESTAMP,
    submitted_at             TIMESTAMP,
    reviewer_id              UUID         REFERENCES admin_users(id),
    review_comment           TEXT,
    reviewed_at              TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);

-- Une seule proposition en cours par formation et par auteur
CREATE UNIQUE INDEX uq_course_content_proposals_active ON course_content_proposals (bootcamp_id, author_id)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'CHANGES_REQUESTED') AND is_deleted = FALSE;
CREATE INDEX idx_course_content_proposals_status ON course_content_proposals (status, updated_at DESC);

-- Nouveau droit : le formateur propose ; l'ancien profil « Gestion complète » a tous les droits sauf comptes/profils/audit
-- (voir DefaultProfiles). La Direction a tous les droits sans qu'ils soient stockés.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'PROGRAM_PROPOSE' FROM roles r WHERE r.name IN ('ROLE_TRAINER', 'ROLE_ADMIN')
ON CONFLICT DO NOTHING;
UPDATE roles SET description = 'Animation de ses sessions uniquement : appel, correction des projets, réponses aux questions, messages ; propositions de modification du programme.'
 WHERE name = 'ROLE_TRAINER';
