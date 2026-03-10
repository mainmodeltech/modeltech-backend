-- Lien inscription -> session
ALTER TABLE registrations ADD COLUMN bootcamp_session_id UUID;
ALTER TABLE registrations ADD COLUMN session_name VARCHAR(255);
ALTER TABLE registrations ADD CONSTRAINT fk_registration_session
    FOREIGN KEY (bootcamp_session_id) REFERENCES bootcamp_sessions(id) ON DELETE SET NULL;
CREATE INDEX idx_registrations_session ON registrations(bootcamp_session_id);

-- Code promo utilise lors de l'inscription
ALTER TABLE registrations ADD COLUMN promo_code_id UUID;
ALTER TABLE registrations ADD COLUMN promo_code_used VARCHAR(50);
ALTER TABLE registrations ADD COLUMN discount_percent INTEGER;
