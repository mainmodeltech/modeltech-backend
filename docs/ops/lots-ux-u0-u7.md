# Lots UX (U0 à U7) — notes de mise en production

Empilés sur la phase 3a (profils et droits). À déployer dans l'ordre des branches : `phase-3-profils` → `u6` → `u4` → `u3` → `u1` → `u2` → `u5` (backend) ; côté front, la chaîne se termine par `ux/u7b-contenus`.

## Migrations Flyway

| Version | Objet |
|---|---|
| V34 | Certificats : statut `PENDING` (« À valider »), colonne `eligible_at`, index unique sur les certificats `VALID`/`PENDING` d'un même apprenant et d'une même formation |
| V35 | `quiz_attempt_grants` : tentatives supplémentaires accordées par apprenant et par quiz |
| V36 | Communauté : contrainte de contenu portée à 5 000 caractères |
| V37 | table `masterclasses`, colonne `attendance` sur `masterclass_registrations`, droit `MASTERCLASS_MANAGE` (Communication, Pédagogie, Admin) |

## Nouveaux droits

- `SESSIONS_ASSIGN` : affecter un formateur à une session.
- `MASTERCLASS_MANAGE` : créer/modifier les éditions de masterclass, pointer la présence, écrire aux inscrits.
- `GET /admin/questions` (boîte de réception des questions de leçon) exige `SESSIONS_VIEW`.

Après déploiement, vérifier la matrice dans **Profils** : les profils métier déjà personnalisés ne reçoivent pas automatiquement les nouveaux droits.

## Changements de comportement à connaître

- **Certificats** : ils sont désormais créés « À valider ». Ceux déjà émis restent valides. Rien n'est visible de l'apprenant ni envoyé par e-mail avant la validation manuelle (*Certificats* → Valider / Valider la sélection). La vérification publique et le PDF répondent 404 tant que le certificat est en attente.
- **Codes promo** : plafond de **30 %** (constante `PromoCode.MAX_DISCOUNT_PERCENT`). Un code existant au-dessus est conservé en base, marqué « Plafond dépassé » dans l'écran, et **ignoré** à l'inscription et à la validation. À corriger par la direction, **sans avancer tant qu'il en reste un**. Requête de contrôle :

  ```sql
  SELECT code, discount_percent FROM promo_codes WHERE discount_percent > 30;
  ```
- **Quiz** : la limite de tentatives est lue en direct ; la direction ou la pédagogie peut en plus accorder des tentatives à un apprenant précis.
- **Inscriptions** : l'entrée de menu est retirée, l'adresse redirige vers *Candidatures* (historique dans l'onglet *Archives*). Le code de l'ancien écran sera supprimé dans une version ultérieure.
- **Masterclass** : une édition = une ligne dans `masterclasses` (plusieurs par an). Les inscriptions de la page publique visent la prochaine édition publiée.

## Configuration

| Variable / propriété | Défaut | Rôle |
|---|---|---|
| `UPLOAD_MAX_FILE_SIZE` | 100MB | Limite multipart par fichier (le contrôle fin par type reste côté service) |
| `UPLOAD_MAX_REQUEST_SIZE` | 105MB | Limite multipart par requête |
| `app.rate-limit.masterclass.per-hour` | 5 | Inscriptions masterclass par adresse et par heure |

Un filtre (`UploadSizeFilter`) refuse tôt, sur l'en-tête `Content-Length`, les envois trop volumineux avec un message lisible. Si un reverse proxy est placé devant l'API, aligner sa limite (`client_max_body_size` pour nginx) sur ces valeurs.

Les images (logo, photo, couverture, captures) sont validées par signature binaire et extension ; les SVG sont refusés s'ils contiennent des scripts ; les exécutables sont refusés.

## Vérifications après déploiement

1. `GET /api/v1/admin/profiles` répond 200 pour la direction.
2. Un candidat de test passe de *Nouvelle* à *Inscrite*, le paiement hors site apparaît dans *Paiements*.
3. Un certificat éligible apparaît « À valider » et n'est pas envoyé.
4. Création d'une édition de masterclass et inscription depuis la page publique.
