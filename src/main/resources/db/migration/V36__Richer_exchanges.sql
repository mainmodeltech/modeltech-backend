-- Échanges avec du code : messages d'entraide et questions peuvent contenir des blocs de code (plus longs qu'une phrase).
-- Les messages restent du texte : la mise en forme (Markdown simple) est interprétée à l'affichage, jamais comme du HTML.
ALTER TABLE community_posts DROP CONSTRAINT IF EXISTS community_posts_content_check;
ALTER TABLE community_posts ADD CONSTRAINT community_posts_content_check CHECK (char_length(content) BETWEEN 1 AND 5000);
