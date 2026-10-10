-- Notes personnelles d'un apprenant sur une leçon (synchronisées entre ses appareils).
CREATE TABLE lesson_notes (
    id          UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id  UUID NOT NULL REFERENCES learners(id),
    lesson_id   UUID NOT NULL REFERENCES course_lessons(id),
    content     TEXT NOT NULL DEFAULT '' CHECK (char_length(content) <= 10000),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_lesson_notes UNIQUE (learner_id, lesson_id)
);
CREATE INDEX idx_lesson_notes_learner ON lesson_notes (learner_id);
