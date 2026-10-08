-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "messaging" : rappels de lives, messages aux apprenants d'une session, questions des apprenants.
-- Additif, aucune donnée semée.
-- ─────────────────────────────────────────────────────────────────────────────

-- Rappels déjà envoyés (un par live, session et moment) : empêche les doublons malgré les passages répétés du planificateur
CREATE TABLE live_reminders (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    lesson_id  UUID        NOT NULL REFERENCES course_lessons(id),
    session_id UUID        NOT NULL REFERENCES bootcamp_sessions(id),
    kind       VARCHAR(10) NOT NULL CHECK (kind IN ('H24', 'H1')),
    recipients INTEGER     NOT NULL DEFAULT 0,
    sent_at    TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_live_reminders UNIQUE (lesson_id, session_id, kind)
);

-- Messages envoyés par l'équipe aux apprenants d'une session (historique, pas le détail par destinataire)
CREATE TABLE session_messages (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id      UUID         NOT NULL REFERENCES bootcamp_sessions(id),
    subject         VARCHAR(255) NOT NULL,
    body            TEXT         NOT NULL,
    sent_by         VARCHAR(255) NOT NULL,
    recipient_count INTEGER      NOT NULL,
    delivered_count INTEGER,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_session_messages_session ON session_messages (session_id, created_at DESC);

-- « Poser une question » depuis le lecteur de cours
CREATE TABLE lesson_questions (
    id          UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id  UUID    NOT NULL REFERENCES learners(id),
    lesson_id   UUID    NOT NULL REFERENCES course_lessons(id),
    session_id  UUID    REFERENCES bootcamp_sessions(id),
    question    TEXT    NOT NULL,
    answer      TEXT,
    answered_by VARCHAR(255),
    answered_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_lesson_questions_learner ON lesson_questions (learner_id, lesson_id);
CREATE INDEX idx_lesson_questions_session_open ON lesson_questions (session_id) WHERE answer IS NULL AND is_deleted = FALSE;
