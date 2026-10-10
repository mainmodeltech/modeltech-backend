-- Communauté : fil d'entraide par formation (messages et réponses d'un niveau), modéré par l'équipe.
CREATE TABLE community_posts (
    id          UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    bootcamp_id UUID NOT NULL REFERENCES bootcamps(id),
    learner_id  UUID NOT NULL REFERENCES learners(id),
    parent_id   UUID REFERENCES community_posts(id),
    content     TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255)
);
CREATE INDEX idx_community_posts_bootcamp ON community_posts (bootcamp_id, created_at DESC);
CREATE INDEX idx_community_posts_parent ON community_posts (parent_id);
