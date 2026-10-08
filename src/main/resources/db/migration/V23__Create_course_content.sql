-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "course-content" : programme d'une formation (modules, leçons, ressources),
-- règles du cours / du certificat, progression des apprenants.
-- Additif. Aucune donnée semée : une formation sans ligne n'a pas de programme.
-- ─────────────────────────────────────────────────────────────────────────────

-- Réglages du cours et règles du certificat (une ligne par formation, créée à la 1re sauvegarde)
CREATE TABLE course_configs (
    id                         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    bootcamp_id                UUID        NOT NULL REFERENCES bootcamps(id),
    sequential_unlock          BOOLEAN     NOT NULL,
    access_duration            VARCHAR(20) NOT NULL CHECK (access_duration IN ('12_MONTHS', 'LIFETIME')),
    lessons_completed_percent  INTEGER     NOT NULL CHECK (lessons_completed_percent BETWEEN 0 AND 100),
    quiz_pass_percent          INTEGER     NOT NULL CHECK (quiz_pass_percent BETWEEN 0 AND 100),
    live_presence_percent      INTEGER     NOT NULL CHECK (live_presence_percent BETWEEN 0 AND 100),
    final_project_validated    BOOLEAN     NOT NULL,
    certificate_template       VARCHAR(255) NOT NULL,
    content_updated_at         TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_course_configs_bootcamp UNIQUE (bootcamp_id)
);

CREATE TABLE course_modules (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    bootcamp_id UUID         NOT NULL REFERENCES bootcamps(id),
    position    INTEGER      NOT NULL,
    title       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_course_modules_bootcamp ON course_modules (bootcamp_id) WHERE is_deleted = FALSE;

CREATE TABLE course_lessons (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    module_id           UUID         NOT NULL REFERENCES course_modules(id),
    position            INTEGER      NOT NULL,
    title               VARCHAR(255) NOT NULL,
    subtitle            VARCHAR(255),
    type                VARCHAR(20)  NOT NULL CHECK (type IN ('VIDEO', 'RESOURCE', 'LIVE', 'QUIZ')),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('PUBLISHED', 'SCHEDULED', 'DRAFT')),
    duration_seconds    INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    video_provider_id   VARCHAR(255),
    video_url           VARCHAR(2048),
    description         TEXT,
    live_at             TIMESTAMP,
    live_url            VARCHAR(2048),
    quiz_question_count INTEGER CHECK (quiz_question_count IS NULL OR quiz_question_count >= 1),
    quiz_pass_threshold INTEGER CHECK (quiz_pass_threshold IS NULL OR quiz_pass_threshold BETWEEN 1 AND 100),
    quiz_max_attempts   INTEGER CHECK (quiz_max_attempts IS NULL OR quiz_max_attempts >= 1),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_course_lessons_module ON course_lessons (module_id) WHERE is_deleted = FALSE;

CREATE TABLE lesson_resources (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    lesson_id          UUID         NOT NULL REFERENCES course_lessons(id),
    position           INTEGER      NOT NULL,
    name               VARCHAR(255) NOT NULL,
    file_type          VARCHAR(20)  NOT NULL,
    size_label         VARCHAR(50),
    note               VARCHAR(500),
    url                VARCHAR(2048),
    locked_until_quiz  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_lesson_resources_lesson ON lesson_resources (lesson_id) WHERE is_deleted = FALSE;

-- Progression : une ligne par (apprenant, leçon)
CREATE TABLE lesson_progress (
    id               UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id       UUID    NOT NULL REFERENCES learners(id),
    lesson_id        UUID    NOT NULL REFERENCES course_lessons(id),
    completed        BOOLEAN NOT NULL DEFAULT FALSE,
    position_seconds INTEGER NOT NULL DEFAULT 0 CHECK (position_seconds >= 0),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_lesson_progress UNIQUE (learner_id, lesson_id)
);
CREATE INDEX idx_lesson_progress_learner ON lesson_progress (learner_id);
