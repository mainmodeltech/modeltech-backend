-- Simulateur de certification : deux nouveaux types de questions de l'examen PL-300.
--   MATCHING     : glisser-déposer, associer des éléments à des options
--   SHORT_ANSWER : réponse rédigée, validée par mots-clés
ALTER TABLE sim_item_version DROP CONSTRAINT sim_item_version_type_check;
ALTER TABLE sim_item_version
    ADD CONSTRAINT sim_item_version_type_check
        CHECK (type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'YES_NO_SERIES', 'MATCHING', 'SHORT_ANSWER'));
