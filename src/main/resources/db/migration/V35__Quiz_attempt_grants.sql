-- Tentatives de quiz supplémentaires accordées à un apprenant (erreur de réglage, circonstance exceptionnelle).
-- La limite effective d'un apprenant sur un quiz = limite du quiz + somme de ses tentatives accordées. Chaque octroi garde son motif.

CREATE TABLE quiz_attempt_grants (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id      UUID         NOT NULL REFERENCES learners(id),
    lesson_id       UUID         NOT NULL REFERENCES course_lessons(id),
    extra_attempts  INTEGER      NOT NULL CHECK (extra_attempts BETWEEN 1 AND 10),
    reason          TEXT         NOT NULL,
    granted_by      VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);

CREATE INDEX idx_quiz_attempt_grants_learner_lesson ON quiz_attempt_grants (learner_id, lesson_id) WHERE is_deleted = FALSE;
