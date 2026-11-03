# Paiement — lot L1 : réglages (Direction), secours manuel, frais configurables

Ce lot prépare l'harmonisation du paiement de **tous** les services (simulateur, inscriptions, coaching). Il est **additif** : sans aucun réglage, le comportement de staging reste celui d'avant (le simulateur se paie en ligne, les inscriptions et le coaching restent en manuel).

## Ce que le lot apporte
- **Réglages de paiement** (*Équipe & comptes → Réglages de paiement*, **Direction seule**, droit `PAYMENT_SETTINGS_MANAGE`) :
  - un **interrupteur général** du paiement en ligne (coupure d'urgence) ;
  - un **mode par service** : *Manuel seulement*, *En ligne ou manuel, au choix du client*, *En ligne (le manuel prend le relais en cas de problème)* ;
  - l'état de la passerelle (configurée ou non, test ou production, panne détectée) et l'adresse de notification ;
  - **motif obligatoire** à chaque changement, consigné dans le journal d'audit (`PAYMENT_SETTINGS_CHANGED`) ; aucun changement n'est écrit si rien ne change.
- **Le manuel est toujours un secours** : si l'interrupteur est coupé, si les clés manquent ou si PayDunya est en panne, le client se voit proposer Wave / Orange Money / virement avec un message d'explication. Il y a toujours au moins un moyen de payer.
- **Disjoncteur** : 3 échecs de création de paiement de suite → le manuel est proposé pendant 10 minutes sans attendre PayDunya ; un paiement réussi remet le compteur à zéro ; la Direction peut *Remettre en service* à la main.
- **Paiement manuel du simulateur** : la commande est créée sans appel au prestataire, l'acheteur déclare son moyen et la référence de la transaction (+ capture facultative), l'équipe confirme ou refuse (motif) ; l'accès ne s'ouvre qu'à la confirmation. Un refus renvoie la commande « en attente » avec le motif visible par l'acheteur, qui peut déclarer de nouveau.
- **Frais du prestataire configurables sans changer le code** (variables ci-dessous) : absorbés (prix du catalogue inchangé) ou répercutés sur le **paiement en ligne seulement**.

## Configuration (variables d'environnement du backend)
| Variable | Rôle |
|---|---|
| `PAYMENTS_FEES_MODE` | `ABSORB` (défaut) : le client paie le prix du catalogue. `PASS_ON` : le client paie le prix **plus** les frais, en ligne seulement |
| `PAYMENTS_FEES_PERCENT` | Taux des frais en % (ex. `3.5`), de 0 inclus à 20 exclu |
| `PAYMENTS_FEES_FIXED_XOF` | Part fixe en FCFA, de 0 à 5 000 |

Formule en `PASS_ON` : `total = arrondi supérieur((prix + part fixe) / (1 − taux/100))`, pour qu'il reste au moins le prix après prélèvement des frais. Exemple : 45 000 FCFA avec 3,5 % → 46 633 FCFA (dont 1 633 de frais). Une valeur incohérente **empêche le démarrage** (mieux qu'un mauvais prix).

Ces trois variables sont déjà dans `docker-compose.yml` et `docker-compose.staging.yml` (sans elles dans le compose, elles n'atteignent pas le conteneur) et documentées dans `.env.example` / `.env.staging.example`. À renseigner dans l'interface Dokploy, puis redémarrer le backend. Le taux réel est celui de **votre grille PayDunya** (à confirmer avec votre compte).

## Garde-fous (ce que le code garantit)
- Le montant encaissé en ligne est **figé à la commande** (`charged_xof`) et **revérifié** auprès de PayDunya : un paiement confirmé pour un autre montant (par exemple le prix sans les frais) ouvre **aucun accès** et la commande passe en « Échec ».
- Un paiement manuel n'ouvre jamais d'accès sans confirmation de l'équipe (droits `SIMULATOR_MANAGE` ou `PAYMENTS_MANAGE`) ; confirmer deux fois est refusé ; **un seul accès** par commande.
- Une commande manuelle jamais déclarée est abandonnée après 7 jours ; une commande **déclarée** n'est jamais abandonnée automatiquement (un humain doit trancher).
- Le justificatif est stocké en privé ; l'équipe n'y accède que par un lien signé de 10 minutes ; il n'apparaît jamais dans le suivi public.
- Les réglages ne contiennent aucune clé ; l'API ne les renvoie jamais.
- Un payeur entreprise (sur facture) est toujours en manuel : ce n'est pas une panne.

## Migration
`V46__Payment_settings_and_manual_orders.sql` : tables `payment_setting` (singleton) et `payment_service_setting` (valeurs par défaut : SIMULATOR en ligne seul, REGISTRATION et COACHING en manuel), colonnes de canal / frais / déclaration / confirmation sur `sim_order`, nouveau statut `DECLARED`. Les commandes existantes sont migrées en « en ligne » avec leur montant d'origine.

## Mise en service (checklist)
1. Déployer ; la migration s'applique seule. Les réglages par défaut conservent le comportement actuel.
2. *Réglages de paiement* (compte Direction) : vérifier l'état de PayDunya et l'adresse de notification.
3. Décider du mode du simulateur (par exemple *En ligne ou manuel* pendant la recette) et de la politique de frais (`ABSORB` ou `PASS_ON`).
4. Exécuter les parcours P53 à P65 du guide de recette.
5. **Seule la Direction** peut modifier ces réglages : le profil « Gestion complète » (Admin) ne les voit pas.

## Limites connues (volontaires à ce stade)
- Les **inscriptions** et le **coaching** ne passent pas encore par la passerelle : leur mode est déjà réglable ici, mais le paiement en ligne de ces services arrive aux lots suivants (échéances et solde complet, lien de paiement libre).
- Le simulateur propose le virement sans coordonnées bancaires affichées : l'acheteur est invité à les demander à l'équipe (comme sur la page de paiement des inscriptions).
- Le remboursement reste manuel avec validation à quatre yeux.
- Le taux de frais n'est pas lu automatiquement chez PayDunya : il se renseigne à la main (variables ci-dessus).
