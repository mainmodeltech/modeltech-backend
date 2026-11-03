# Paiement — lot L2 : paiement en ligne des inscriptions (échéances et solde)

Ce lot branche les inscriptions aux formations sur la même passerelle que le simulateur (PayDunya), avec le même secours manuel et les mêmes réglages de la Direction (lot L1). **Par défaut rien ne change** : le service « Inscriptions aux formations » reste en *Manuel seulement* tant que la Direction ne l'ouvre pas dans *Réglages de paiement*.

## Parcours
1. Le candidat dépose sa candidature ; **l'équipe l'examine et l'accepte** (validation humaine inchangée). L'acceptation fixe le prix et les échéances et envoie le **lien de paiement personnel** `…/paiement/<jeton>`.
2. Sur ce lien, selon le réglage :
   - **Payer en ligne** : « Payer l'échéance N » ou « Payer tout le solde (n échéances) » (le solde n'apparaît que s'il reste au moins deux échéances à régler). Le montant affiché vient du serveur, frais compris.
   - **Manuel** (Wave, Orange Money, virement + déclaration + justificatif) : inchangé, vérifié par l'équipe à quatre yeux.
3. En ligne : le candidat est redirigé chez PayDunya, paie, revient sur son lien (`?checkout=<référence>`). Le serveur **relit le paiement chez PayDunya** (notification ou retour) puis confirme **automatiquement** les échéances couvertes (moyen « Paiement en ligne », confirmé par « paiement-en-ligne »). La première échéance confirmée ouvre l'inscription comme avant : place comptée (dépassement toléré), compte apprenant, accès, e-mail.
4. Remboursements : inchangés, **manuels** avec demande et validation par une autre personne.

## Ce que le code garantit
- **Rien n'est confirmé sur la foi du navigateur ni d'une notification** : le prestataire est interrogé (jeton → statut, montant, référence, mode) via le même vérificateur que le simulateur (`OnlineCheckoutVerifier`) ; le montant attendu est celui **figé à la création** de la tentative (`payment_checkout.charged_xof`).
- **Une seule confirmation** : verrou de ligne sur la tentative pendant la vérification ; une notification reçue deux fois ou en même temps qu'un retour du client confirme une fois.
- **Incohérence = rien de confirmé** : autre montant, autre référence ou autre mode → la tentative passe en « Échec » avec une ligne d'audit `PAYMENT_ONLINE_MISMATCH` et une erreur dans les journaux (« à contrôler manuellement »).
- **Pas de double règlement silencieux** : si une échéance couverte a entre-temps été réglée à la main (ou annulée, ou l'inscription close), le paiement en ligne n'est pas compté : même alerte, un humain tranche (remboursement ou report).
- **Un même lien n'est pas créé deux fois** : la même demande dans la demi-heure reprend la tentative ouverte ; au plus 8 tentatives par heure et par inscription.
- Une échéance **déclarée** à la main (en cours de vérification) ne se paie pas en ligne ; une échéance **en attente** oui.
- **Entreprises (facture, bon de commande) : toujours en manuel**, jamais de paiement en ligne.
- Quand le service est réglé *En ligne* et que la passerelle répond, la déclaration manuelle est refusée ; elle redevient possible si la passerelle est coupée, non configurée ou en panne (disjoncteur du lot L1).
- Le moyen « Paiement en ligne » est posé par le serveur : la saisie manuelle d'un paiement par l'équipe et la déclaration du candidat le refusent.
- Tentatives non payées : revérifiées toutes les 5 minutes, abandonnées après 48 h.

## Configuration
Aucune nouvelle variable. Réutilise `PAYDUNYA_*` (passerelle) et `PAYMENTS_FEES_*` (frais absorbés ou répercutés, appliqués au montant encaissé en ligne, une seule fois pour un règlement du solde). Optionnel : `app.payments.checkouts-sweep-ms` (période du rapprochement).

## Migration
`V47__Registration_online_checkouts.sql` : tables `payment_checkout` et `payment_checkout_item` ; la contrainte de `payments.method` accepte `PAYDUNYA`.

## Mise en service (checklist)
1. Déployer (la migration s'applique seule). Aucun changement visible tant que le service reste en *Manuel seulement*.
2. Vérifier PayDunya (clés, adresse de notification) dans *Réglages de paiement*.
3. Dans *Réglages de paiement* : passer **Inscriptions aux formations** sur *En ligne ou manuel* le temps de la recette.
4. Exécuter les parcours P66 à P78 du guide de recette (sandbox PayDunya).
5. Quand la recette est bonne, choisir le mode définitif (*En ligne* avec manuel en secours, ou *En ligne ou manuel*).

## Limites connues (volontaires à ce stade)
- Une tentative « Échec » incohérente n'a pas d'écran dédié : elle se retrouve dans le *Journal d'audit* (catégorie Paiements, action « Paiement en ligne encaissé mais incohérent »). Un écran de suivi des paiements en ligne est à prévoir avant l'ouverture publique.
- Le reçu (PDF) d'une échéance payée en ligne indique « Paiement en ligne (Wave, Orange Money, carte) » : le sous-moyen exact choisi chez PayDunya n'est pas conservé.
- Les e-mails de relance d'échéance restent envoyés comme avant ; ils pointent vers le même lien, qui propose désormais le paiement en ligne.
- L'API PayDunya réelle (sandbox) n'a pas encore été exercée : à valider en recette.
