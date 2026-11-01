-- Simulateur de certification (lot S0) : la liste d'attente est un message de contact d'un nouveau type,
-- ce qui la rend visible dans « Messages » avec ses notifications, sans table supplémentaire.
ALTER TABLE contact_messages DROP CONSTRAINT contact_messages_type_check;
ALTER TABLE contact_messages
    ADD CONSTRAINT contact_messages_type_check
        CHECK (type IN ('CONTACT', 'DIAGNOSTIC', 'PARTNER_APPLICATION', 'SIMULATOR_WAITLIST'));
