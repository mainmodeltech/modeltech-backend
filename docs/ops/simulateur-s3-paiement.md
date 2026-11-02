# Simulateur PL-300 — lot S3 : paiement en ligne (PayDunya)

## Ce que le lot apporte
- **Forfaits en vente** (`sim_plan`) : Essentiel, Pro, Pro + coaching. Prix, durée d'accès et nombre d'examens blancs se modifient dans *Simulateur → Forfaits* (tracé dans l'audit). **Les prix de départ sont des hypothèses à valider.**
- **Achat en ligne** : le visiteur choisit un forfait, laisse son nom et son e-mail, est redirigé vers la page de paiement PayDunya (Wave, Orange Money, carte…), puis revient sur le site. Son accès s'ouvre **automatiquement** dès que le paiement est confirmé, et il reçoit un e-mail de confirmation + un lien de connexion.
- **Suivi** : *Simulateur → Commandes* (statut, montant, mode test/réel, bouton « Revérifier ») et état de la passerelle.

## Parcours technique
1. `POST /api/v1/simulator/orders` (public, limité par IP) : crée la commande (prix **décidé par le serveur**) et la facture chez PayDunya ; renvoie l'adresse de paiement.
2. Le client paie chez PayDunya, qui (a) **notifie** `POST /api/v1/payments/paydunya/ipn` (IPN) et (b) redirige le client vers `…/simulateur-pl-300/paiement?ref=<référence opaque>`.
3. Dans les deux cas, le serveur **relit l'état du paiement chez PayDunya** (`GET /checkout-invoice/confirm/{jeton}` avec nos clés) : seul ce résultat fait foi.
4. Si le paiement est confirmé **et** cohérent (montant, référence de commande, mode), la commande passe à « Payée », **un seul** accès est ouvert, l'audit est écrit, les e-mails partent.
5. Filet de sécurité : toutes les 5 minutes, les commandes encore en attente sont revérifiées ; au bout de 48 h sans paiement, elles sont abandonnées.

## Sécurité (ce que le code garantit)
- **Rien n'est cru sans vérification** : le corps d'une notification ne sert qu'à retrouver la commande ; l'état est relu chez PayDunya. Un jeton inconnu est ignoré. Le « hash » joint (SHA-512 de la clé maître) est un contrôle supplémentaire : un hash faux fait ignorer la notification (le rapprochement rattrape un vrai paiement).
- **Le prix ne vient jamais du navigateur** et est figé à la commande (un changement de tarif ne touche pas les commandes ouvertes).
- **Un seul accès par commande** : verrou de ligne pendant la vérification + contrainte unique en base. Une notification reçue deux fois, ou en même temps que le retour du client, ouvre un seul accès.
- **Incohérence = rien d'ouvert** : montant, référence ou mode (test/réel) différents → commande en « Échec » avec le motif et une erreur dans les journaux (« à contrôler manuellement »).
- **Clés uniquement dans l'environnement**, jamais journalisées ; la réponse de l'API admin ne les contient pas.
- L'adresse de paiement renvoyée par le prestataire doit être en **HTTPS** (seule exception : une adresse de test locale explicitement configurée).
- La référence de commande (adresse de retour) est un identifiant aléatoire de 122 bits ; le suivi public ne révèle qu'une adresse e-mail **masquée** (`a***@gmail.com`), jamais le jeton PayDunya.
- Adresses jetables et adresses du personnel refusées ; 5 commandes en attente par heure et par acheteur ; limitation par IP.

## Configuration (variables d'environnement du backend)
| Variable | Rôle |
|---|---|
| `PAYDUNYA_MODE` | `sandbox` (essais, aucun argent réel — défaut) ou `live` |
| `PAYDUNYA_MASTER_KEY`, `PAYDUNYA_PRIVATE_KEY`, `PAYDUNYA_TOKEN` | Clés de l'application PayDunya **du même mode** (test avec test, production avec production). Vides = achat désactivé (aucun bouton « Acheter ») |
| `PAYDUNYA_STORE_NAME` | Nom affiché sur la page de paiement (défaut : Model Technologie) |
| `PAYDUNYA_CALLBACK_URL` | Adresse de notification, ex. `https://api-staging.model-technologie.com/api/v1/payments/paydunya/ipn` (affichée dans *Commandes* pour contrôle) |
| `APP_FRONTEND_URL` | Déjà existante : sert aux adresses de retour/annulation |

**Adresses de notification (IPN) à déclarer chez PayDunya**
- Staging : `https://api-staging.model-technologie.com/api/v1/payments/paydunya/ipn`
- Production : `https://api.model-technologie.com/api/v1/payments/paydunya/ipn`

## Mise en service (checklist)
1. Créer l'application PayDunya **de test** avec l'adresse de notification du staging ; renseigner les trois clés de test + `PAYDUNYA_MODE=sandbox` + `PAYDUNYA_CALLBACK_URL` dans l'environnement du backend de staging (Dokploy), redéployer.
2. Dans *Simulateur → Commandes* : la bannière indique « PayDunya — Mode test » et l'adresse de notification.
3. Dérouler la recette P42 à P52 avec les **comptes de test fictifs** de PayDunya.
4. Après le KYC : créer l'application de **production**, renseigner ses clés + `PAYDUNYA_MODE=live` + l'adresse de production, redéployer, faire **un vrai paiement du forfait le moins cher** et le contrôler de bout en bout (accès, e-mails, reçu).
5. Vérifier l'envoi d'e-mails (domaine d'expédition) : sans lui, l'accès s'ouvre mais l'acheteur ne reçoit ni confirmation ni lien (il peut demander un lien depuis la page de connexion).

## Limites connues (volontaires à ce stade)
- **Pas de remboursement automatique** : un remboursement se fait chez PayDunya, puis on **révoque** l'accès dans *Droits d'usage*.
- **Pas de coupons** ni de facture PDF (le reçu PayDunya est joint à l'e-mail et à la page de retour).
- Conditions d'utilisation et de vente : la case à cocher existe, **le texte juridique reste à faire valider** (politique de remboursement, durée d'accès, interdiction de copie).
- Un acheteur qui paie sans jamais revenir sur le site est traité par la notification ou, au plus tard, par le rapprochement (5 minutes).
