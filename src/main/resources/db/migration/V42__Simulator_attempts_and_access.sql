-- Simulateur de certification (lot S2) : droits d'usage, examens assemblés (instantané figé), tentatives, réponses.
-- Le serveur est autoritaire : échéance, quota et notation vivent ici, jamais dans le navigateur.

CREATE TABLE sim_access_grant (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id          UUID         NOT NULL REFERENCES learners (id),
    certification_id    UUID         NOT NULL REFERENCES sim_certification (id),
    plan_code           VARCHAR(20)  NOT NULL CHECK (plan_code IN ('TRIAL', 'ESSENTIEL', 'PRO', 'PRO_COACHING', 'ENTREPRISE')),
    starts_at           TIMESTAMP    NOT NULL,
    expires_at          TIMESTAMP    NOT NULL,
    exam_attempts_total INTEGER      NOT NULL CHECK (exam_attempts_total >= 0),
    exam_attempts_used  INTEGER      NOT NULL DEFAULT 0,
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    granted_by          VARCHAR(255) NOT NULL,
    reason              TEXT,
    source_payment_id   UUID,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at          TIMESTAMP,
    revoked_by          VARCHAR(255),
    revoke_reason       TEXT,
    CONSTRAINT ck_sim_grant_dates CHECK (expires_at > starts_at),
    CONSTRAINT ck_sim_grant_used CHECK (exam_attempts_used BETWEEN 0 AND exam_attempts_total)
);
CREATE INDEX idx_sim_grant_learner ON sim_access_grant (learner_id, status);

-- Un examen assemblé : instantané des versions de questions proposées (ce que l'apprenant a réellement vu)
CREATE TABLE sim_exam_form (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    blueprint_id UUID        NOT NULL REFERENCES sim_blueprint (id),
    mode         VARCHAR(5)  NOT NULL CHECK (mode IN ('EXAM', 'STUDY')),
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE sim_exam_form_item (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    form_id         UUID        NOT NULL REFERENCES sim_exam_form (id),
    position        INTEGER     NOT NULL CHECK (position > 0),
    item_version_id UUID        NOT NULL REFERENCES sim_item_version (id),
    domain_code     VARCHAR(10) NOT NULL,
    skill_code      VARCHAR(30) NOT NULL,
    CONSTRAINT uq_sim_form_position UNIQUE (form_id, position),
    CONSTRAINT uq_sim_form_item UNIQUE (form_id, item_version_id)
);
CREATE INDEX idx_sim_form_item_version ON sim_exam_form_item (item_version_id);

CREATE TABLE sim_attempt (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id      UUID         NOT NULL REFERENCES learners (id),
    grant_id        UUID         NOT NULL REFERENCES sim_access_grant (id),
    form_id         UUID         NOT NULL UNIQUE REFERENCES sim_exam_form (id),
    mode            VARCHAR(5)   NOT NULL CHECK (mode IN ('EXAM', 'STUDY')),
    status          VARCHAR(12)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    started_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    deadline_at     TIMESTAMP,
    finished_at     TIMESTAMP,
    score_percent   NUMERIC(5, 2),
    scaled_score    INTEGER,
    passed          BOOLEAN,
    result          JSONB,
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_sim_attempt_learner ON sim_attempt (learner_id, started_at DESC);
CREATE INDEX idx_sim_attempt_open ON sim_attempt (deadline_at) WHERE status = 'IN_PROGRESS';
-- Un seul examen chronométré en cours par apprenant
CREATE UNIQUE INDEX uq_sim_one_exam_in_progress ON sim_attempt (learner_id) WHERE status = 'IN_PROGRESS' AND mode = 'EXAM';

CREATE TABLE sim_attempt_answer (
    id                 UUID      NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    attempt_id         UUID      NOT NULL REFERENCES sim_attempt (id),
    form_item_id       UUID      NOT NULL REFERENCES sim_exam_form_item (id),
    answer             JSONB,
    flagged            BOOLEAN   NOT NULL DEFAULT FALSE,
    time_spent_seconds INTEGER   NOT NULL DEFAULT 0 CHECK (time_spent_seconds >= 0),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sim_answer UNIQUE (attempt_id, form_item_id)
);

CREATE TABLE sim_attempt_event (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    attempt_id  UUID        NOT NULL REFERENCES sim_attempt (id),
    type        VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    meta        JSONB
);
CREATE INDEX idx_sim_attempt_event ON sim_attempt_event (attempt_id, occurred_at);
