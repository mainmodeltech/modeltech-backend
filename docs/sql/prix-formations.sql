-- Montants numeriques (XOF) des formations, a executer UNE FOIS en production apres verification.
-- 1) Lancer d'abord la requete de controle : chaque ligne doit correspondre a la bonne formation.
-- 2) Lancer ensuite les UPDATE. Ils ne touchent que les formations dont le montant est encore vide
--    (jamais d'ecrasement d'un prix saisi dans le back-office).
-- Les prix affiches (colonne "price", texte) ne sont pas modifies ici : a ajuster dans le back-office.

-- Controle
SELECT id, title, price AS prix_affiche, price_amount FROM bootcamps WHERE is_deleted = false ORDER BY title;

UPDATE bootcamps SET price_amount = 150000, currency = 'XOF' WHERE is_deleted = false AND price_amount IS NULL AND title ILIKE '%Power BI%' AND title NOT ILIKE '%Coaching%';
UPDATE bootcamps SET price_amount = 200000, currency = 'XOF' WHERE is_deleted = false AND price_amount IS NULL AND title ILIKE '%Power BI%Coaching%';
UPDATE bootcamps SET price_amount = 100000, currency = 'XOF' WHERE is_deleted = false AND price_amount IS NULL AND title ILIKE '%Excel%VBA%';
UPDATE bootcamps SET price_amount = 75000,  currency = 'XOF' WHERE is_deleted = false AND price_amount IS NULL AND title ILIKE '%python%';
UPDATE bootcamps SET price_amount = 75000,  currency = 'XOF' WHERE is_deleted = false AND price_amount IS NULL AND title ILIKE '%SQL%' AND title NOT ILIKE '%python%';
UPDATE bootcamps SET price_amount = 150000, currency = 'XOF' WHERE is_deleted = false AND price_amount IS NULL AND (title ILIKE '%PSM%' OR title ILIKE '%Scrum%');

-- Verification
SELECT title, price_amount FROM bootcamps WHERE is_deleted = false ORDER BY title;
