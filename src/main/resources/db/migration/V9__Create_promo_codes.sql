CREATE TABLE promo_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),

    -- Parrain
    referrer_name VARCHAR(255) NOT NULL,
    referrer_email VARCHAR(255),
    referrer_phone VARCHAR(50),

    -- Reduction
    discount_percent INTEGER NOT NULL DEFAULT 0,

    -- Limites
    max_uses INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,

    -- Etat
    is_active BOOLEAN NOT NULL DEFAULT true,

    -- Audit (BaseEntity)
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255)
);

CREATE INDEX idx_promo_codes_code ON promo_codes(code);

-- FK depuis registrations vers promo_codes
ALTER TABLE registrations ADD CONSTRAINT fk_registration_promo
    FOREIGN KEY (promo_code_id) REFERENCES promo_codes(id) ON DELETE SET NULL;
