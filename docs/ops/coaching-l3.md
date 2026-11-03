# Coaching — lot L3 : demande, validation par le responsable pédagogique, lien de paiement

Ce lot remplace la demande de coaching « par le formulaire de contact » par un circuit complet et tracé. Le paiement réutilise les réglages de la Direction (lot L1) : par défaut le service « Coaching » reste en *Manuel seulement*.

## Parcours
1. **Demande** : le visiteur remplit `/coaching/demande` (nom, e-mail, besoin ; la formule et le créneau choisis sur la page Coaching pré-remplissent le formulaire). Il accepte d'être contacté. **Aucun paiement n'est demandé.** Il reçoit un accusé de réception, avec le lien de réservation d'un échange de présentation de 15 à 20 minutes s'il est configuré (`COACHING_BOOKING_URL`). L'équipe est alertée par e-mail.
2. **Analyse** (*Catalogue → Demandes de coaching*, droit `COACHING_MANAGE`, profil Pédagogie) : le responsable prend la demande en charge, la consulte, note le formateur envisagé et ses notes internes (jamais vues du client).
3. **Validation** : une fois la disponibilité du formateur **confirmée** (case obligatoire), le responsable fixe l'**offre** et le **montant** et valide. Un lien de paiement personnel (256 bits, valable 30 jours) est créé et envoyé par e-mail au client. Un refus avant validation envoie le motif au client.
4. **Paiement**, sur `/coaching/paiement/<jeton>`, selon les réglages :
   - **en ligne** (PayDunya) : le serveur relit le paiement chez le prestataire avant de confirmer ;
   - **manuel** (Wave, Orange Money, virement + déclaration + justificatif) : la **Finance** (droit `PAYMENTS_MANAGE`) confirme ou refuse avec un motif. Séparation des tâches : la pédagogie valide, la Finance confirme l'argent.
5. **Coaching payé** : le client reçoit sa confirmation ; le formateur et le responsable sont prévenus par e-mail pour **planifier les séances**.
6. Un coaching validé mais pas encore payé peut être **annulé** (le lien devient inutilisable) ou son lien **renvoyé**.

## Ce que le code garantit
- **Le prix ne vient jamais du visiteur** : il est fixé par l'équipe à la validation, puis figé dans la tentative de paiement (frais éventuels compris, `PAYMENTS_FEES_*`).
- **Rien n'est confirmé sur la foi du navigateur ni d'une notification** : même vérificateur que le simulateur et les inscriptions (montant, référence, mode relus chez PayDunya).
- **Argent encaissé alors que le coaching n'attend plus de paiement** (annulé, expiré, déjà réglé) : rien n'est compté, la tentative passe en « Échec » avec une ligne d'audit `PAYMENT_ONLINE_MISMATCH` pour un contrôle humain. Un montant ou une référence incohérents sont traités de même.
- Une seule validation, une seule confirmation (verrou de ligne). Une même demande de paiement en ligne dans la demi-heure reprend le même lien ; au plus 5 tentatives par heure.
- Le lien de paiement et la page publique n'exposent **jamais** l'e-mail, le téléphone, les notes internes ni l'e-mail du formateur.
- Quand le service est réglé *En ligne* et que la passerelle répond, la déclaration manuelle est refusée ; elle redevient possible si la passerelle est coupée, non configurée ou en panne.
- Un lien non payé au bout de 30 jours expire ; une demande dont le client a déclaré un paiement **n'expire jamais** (un humain tranche).
- Le justificatif est privé (lien signé de 10 minutes pour l'équipe).
- Chaque étape est dans le *Journal d'audit* : validation, refus, annulation, déclaration, refus de paiement, paiement.
- Anti-abus du formulaire public : limitation par adresse IP et 3 demandes par jour et par adresse e-mail.

## Configuration
| Variable | Rôle |
|---|---|
| `COACHING_BOOKING_URL` | Lien de réservation d'un échange de présentation (ex. `https://meet.brevo.com/patrick-dooko`). Vide = non proposé |
| `COACHING_NOTIFY_TO` | Destinataires internes (séparés par des virgules) des alertes : nouvelle demande, paiement déclaré. Vide = `NOTIFICATION_EMAIL_TO` |

Déjà ajoutées à `docker-compose.yml`, `docker-compose.staging.yml`, `.env.example` et `.env.staging.example`. La durée de validité du lien (30 jours) se règle par `app.coaching.link-validity-days`.

## Droits et migration
- `COACHING_MANAGE` (« traiter les demandes de coaching ») : profil **Pédagogie** et ancien profil « Gestion complète » ; la Direction a tous les droits. **La migration V48 l'ajoute aux profils existants** (sans quoi un profil déjà en base ne l'aurait pas).
- Lecture de la liste : `COACHING_MANAGE` ou `PAYMENTS_VIEW` (la Finance voit les paiements à vérifier). Confirmer ou refuser un paiement manuel : `PAYMENTS_MANAGE`.
- `V48__Coaching_requests.sql` : tables `coaching_request` et `coaching_checkout`.
- Les contenus par défaut de la page *Coaching* pointent maintenant vers `/coaching/demande` ; les contenus **déjà enregistrés** qui pointent encore vers le formulaire de contact sont redirigés par le site sans action de votre part.

## Mise en service (checklist)
1. Déployer (migration automatique). Renseigner `COACHING_BOOKING_URL` (et `COACHING_NOTIFY_TO` si besoin) dans Dokploy, puis redémarrer le backend.
2. Vérifier que le profil de la personne qui traitera les demandes porte *Pédagogie* (droit « Traiter les demandes de coaching ») et que la Finance peut confirmer les paiements.
3. *Réglages de paiement* : choisir le mode du service « Coaching » (par défaut, manuel).
4. Exécuter les parcours P79 à P92 du guide de recette.

## Limites connues (volontaires à ce stade)
- **Pas encore de pastille** dans le menu pour les demandes à traiter (le compteur est dans les onglets de la page).
- Le **planning des séances** n'est pas géré par la plateforme : après paiement, le formateur prend contact avec le client (e-mail d'alerte). Un échéancier de séances pourra être ajouté ensuite.
- Pas d'accès « espace apprenant » dédié au coaching : le suivi se fait par e-mail.
- Les textes légaux (conditions de vente du coaching, politique de confidentialité) arrivent au lot L4 ; la case d'acceptation du formulaire sera alors reliée à ces textes.
