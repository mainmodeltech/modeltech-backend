-- Conformité juridique (lot L4) : textes légaux versionnés et preuves d'acceptation.
-- Une version publiée est IMMUABLE (le service ne la modifie jamais) : chaque acceptation renvoie à la version exacte lue, avec l'empreinte du texte.

CREATE TABLE legal_document (
    id                   UUID         PRIMARY KEY,
    code                 VARCHAR(12)  NOT NULL CHECK (code IN ('CGU', 'CGV', 'PRIVACY', 'MENTIONS')),
    version              INTEGER      NOT NULL CHECK (version >= 1),
    title                VARCHAR(200) NOT NULL,
    body                 TEXT         NOT NULL,
    status               VARCHAR(12)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    -- Ce qui change par rapport à la version précédente (montré dans l'historique, pas au visiteur)
    change_summary       TEXT,
    content_hash         VARCHAR(64),
    -- Validation par le conseil juridique, obligatoire pour publier
    counsel_validated_by VARCHAR(255),
    counsel_validated_on DATE,
    published_at         TIMESTAMP,
    published_by         VARCHAR(255),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(255),
    updated_at           TIMESTAMP,
    CONSTRAINT uq_legal_document_version UNIQUE (code, version)
);

-- Au plus un brouillon et une version en vigueur par texte
CREATE UNIQUE INDEX uq_legal_one_draft ON legal_document (code) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX uq_legal_one_published ON legal_document (code) WHERE status = 'PUBLISHED';

-- Preuve qu'une personne a accepté une version précise d'un texte, à un moment précis, pour un acte précis
CREATE TABLE legal_acceptance (
    id               UUID         PRIMARY KEY,
    document_id      UUID         NOT NULL REFERENCES legal_document (id),
    document_code    VARCHAR(12)  NOT NULL,
    document_version INTEGER      NOT NULL,
    content_hash     VARCHAR(64)  NOT NULL,
    context          VARCHAR(30)  NOT NULL,
    subject_type     VARCHAR(30)  NOT NULL,
    subject_ref      VARCHAR(64)  NOT NULL,
    email            VARCHAR(255) NOT NULL,
    full_name        VARCHAR(255),
    accepted_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(255)
);

CREATE INDEX idx_legal_acceptance_subject ON legal_acceptance (subject_type, subject_ref);
CREATE INDEX idx_legal_acceptance_email ON legal_acceptance (lower(email));
CREATE INDEX idx_legal_acceptance_document ON legal_acceptance (document_id);
