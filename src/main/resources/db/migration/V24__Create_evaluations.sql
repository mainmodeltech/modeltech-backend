-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "quiz-project-live" : banque de questions, tentatives, projet final,
-- appel des lives. Additif, aucune donnée semée.
-- ─────────────────────────────────────────────────────────────────────────────

-- Banque de questions d'une leçon QUIZ
CREATE TABLE quiz_questions (
    id          UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    lesson_id   UUID    NOT NULL REFERENCES course_lessons(id),
    position    INTEGER NOT NULL,
    text        TEXT    NOT NULL,
    explanation TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_quiz_questions_lesson ON quiz_questions (lesson_id) WHERE is_deleted = FALSE;

CREATE TABLE quiz_choices (
    id          UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    question_id UUID    NOT NULL REFERENCES quiz_questions(id),
    position    INTEGER NOT NULL,
    label       VARCHAR(500) NOT NULL,
    correct     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_quiz_choices_question ON quiz_choices (question_id) WHERE is_deleted = FALSE;

-- Tentatives : questions tirées et réponses figées (la banque peut évoluer ensuite)
CREATE TABLE quiz_attempts (
    id             UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id     UUID    NOT NULL REFERENCES learners(id),
    lesson_id      UUID    NOT NULL REFERENCES course_lessons(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 1),
    started_at     TIMESTAMP NOT NULL,
    submitted_at   TIMESTAMP,
    question_ids   JSONB   NOT NULL,
    answers        JSONB,
    correct_count  INTEGER,
    score          INTEGER CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    passed         BOOLEAN,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_quiz_attempt UNIQUE (learner_id, lesson_id, attempt_number)
);
CREATE INDEX idx_quiz_attempts_learner ON quiz_attempts (learner_id);

-- Projet final : consigne par formation, rendu par apprenant
CREATE TABLE course_projects (
    id                  UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    bootcamp_id         UUID    NOT NULL REFERENCES bootcamps(id),
    brief               TEXT    NOT NULL,
    deadline_label      VARCHAR(100),
    accepted_extensions TEXT[]  NOT NULL,
    max_size_mb         INTEGER NOT NULL CHECK (max_size_mb BETWEEN 1 AND 50),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_course_projects_bootcamp UNIQUE (bootcamp_id)
);

CREATE TABLE project_submissions (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id  UUID        NOT NULL REFERENCES learners(id),
    bootcamp_id UUID        NOT NULL REFERENCES bootcamps(id),
    status      VARCHAR(30) NOT NULL CHECK (status IN ('NOT_STARTED', 'SUBMITTED', 'CHANGES_REQUESTED', 'VALIDATED')),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_project_submission UNIQUE (learner_id, bootcamp_id)
);

CREATE TABLE project_files (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    submission_id UUID         NOT NULL REFERENCES project_submissions(id),
    name          VARCHAR(255) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    object_key    VARCHAR(512) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_project_files_submission ON project_files (submission_id) WHERE is_deleted = FALSE;

CREATE TABLE project_feedback (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    submission_id UUID         NOT NULL REFERENCES project_submissions(id),
    author_name   VARCHAR(255) NOT NULL,
    author_role   VARCHAR(100) NOT NULL,
    message       TEXT         NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_project_feedback_submission ON project_feedback (submission_id);

-- Appel des lives : une ligne par (session, live) une fois l'appel fait, une par apprenant avec sa présence
CREATE TABLE live_roll_calls (
    id         UUID      NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id UUID      NOT NULL REFERENCES bootcamp_sessions(id),
    lesson_id  UUID      NOT NULL REFERENCES course_lessons(id),
    taken_at   TIMESTAMP NOT NULL,
    taken_by   VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_live_roll_call UNIQUE (session_id, lesson_id)
);

CREATE TABLE live_attendance (
    id           UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    roll_call_id UUID    NOT NULL REFERENCES live_roll_calls(id),
    learner_id   UUID    NOT NULL REFERENCES learners(id),
    present      BOOLEAN NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_live_attendance UNIQUE (roll_call_id, learner_id)
);
