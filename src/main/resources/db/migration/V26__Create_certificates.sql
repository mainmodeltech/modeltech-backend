-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "certificates" : certificats de réussite (vérifiables publiquement, PDF généré à la demande).
-- Les données affichées sur le certificat sont figées à la délivrance (nom, formation, signataire…) :
-- renommer ensuite la formation ou le compte ne modifie pas un certificat déjà délivré.
-- ─────────────────────────────────────────────────────────────────────────────

-- Code court de la formation dans le numéro de certificat (ex. VBA → MT-2026-VBA-00042-K7QX).
-- Vide : déduit des initiales du titre.
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS certificate_code VARCHAR(6);
ALTER TABLE bootcamps ADD CONSTRAINT bootcamps_certificate_code_check
    CHECK (certificate_code IS NULL OR certificate_code ~ '^[A-Z0-9]{2,6}$');

CREATE SEQUENCE certificate_number_seq START 1;

CREATE TABLE certificates (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    public_id        VARCHAR(40)  NOT NULL,
    learner_id       UUID         NOT NULL REFERENCES learners(id),
    bootcamp_id      UUID         NOT NULL REFERENCES bootcamps(id),
    session_id       UUID         REFERENCES bootcamp_sessions(id),
    recipient_name   VARCHAR(255) NOT NULL,
    formation_title  VARCHAR(255) NOT NULL,
    duration_label   VARCHAR(100),
    skills           TEXT[],
    includes_project BOOLEAN      NOT NULL DEFAULT FALSE,
    signatory_name   VARCHAR(255) NOT NULL,
    signatory_title  VARCHAR(255) NOT NULL,
    trainer_name     VARCHAR(255),
    issued_at        TIMESTAMP    NOT NULL,
    issued_by        VARCHAR(255) NOT NULL,
    forced           BOOLEAN      NOT NULL DEFAULT FALSE,
    force_reason     TEXT,
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('VALID', 'REVOKED')),
    revoked_at       TIMESTAMP,
    revoked_reason   TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_certificates_public_id UNIQUE (public_id)
);

-- Un seul certificat valide par apprenant et formation (un certificat révoqué peut être redélivré)
CREATE UNIQUE INDEX uq_certificates_valid ON certificates (learner_id, bootcamp_id)
    WHERE status = 'VALID' AND is_deleted = FALSE;
CREATE INDEX idx_certificates_learner ON certificates (learner_id);
CREATE INDEX idx_certificates_bootcamp ON certificates (bootcamp_id);
