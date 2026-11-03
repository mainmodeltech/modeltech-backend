# Conformité juridique — lot L4 : textes légaux, acceptation obligatoire, preuves, cookies

> **Important.** Les quatre textes livrés (CGU, CGV, politique de confidentialité et cookies, mentions légales) sont des **modèles de travail**, pas un avis juridique. Ils doivent être **relus, complétés et validés par votre conseil juridique** avant publication. Le système l'impose : tant que le moindre champ « [À COMPLÉTER] » subsiste, la publication est impossible.

## Ce que le lot apporte
- **Textes légaux versionnés** (*Équipe & comptes → Textes légaux*, **Direction seule**, droit `LEGAL_MANAGE`) : au premier démarrage, un **brouillon** de chaque texte est chargé depuis `src/main/resources/legal/*.md`. La Direction les complète, puis les **publie en indiquant qui les a validés (conseil juridique) et à quelle date**. La version publiée est **figée** ; toute modification ouvre une nouvelle version (l'ancienne est conservée dans l'historique).
- **Pages publiques** : `/cgu`, `/cgv`, `/confidentialite`, `/mentions-legales` (version et date d'entrée en vigueur affichées) et liens dans le pied de page de tout le site.
- **Acceptation obligatoire avant d'aller plus loin**, contrôlée **côté serveur** (le navigateur ne peut pas la contourner) :

| Moment | Textes exigés |
|---|---|
| Achat d'un forfait du simulateur | CGU, CGV, confidentialité |
| Candidature à une formation | CGU, CGV, confidentialité |
| Demande de coaching | CGU, confidentialité |
| Page de paiement d'un coaching (prix connu) | CGV (rien ne peut être payé avant) |
| Espace apprenant : première connexion et à chaque nouvelle version | CGU, confidentialité (fenêtre bloquante) |

- **Preuve d'acceptation** (*Textes légaux → Preuves d'acceptation*) : pour chaque acceptation, le serveur conserve la **version exacte** et l'**empreinte SHA-256** du texte, l'e-mail, l'acte concerné (commande, candidature, demande, apprenant), l'heure, l'adresse IP et le navigateur. Rien n'est modifiable après coup.
- **Cookies** : bandeau d'information ; **la mesure d'audience (Google Analytics) est coupée tant que le visiteur n'a pas accepté** (avant : plus aucun événement envoyé). Refus et acceptation ont le même poids ; lien « Gérer mes cookies » dans le pied de page et sur la page de confidentialité ; un refus ou un retrait efface les cookies de mesure. Choix mémorisé dans le navigateur.

## Comportement au déploiement (sans risque)
- **Rien ne change tant que rien n'est publié** : un texte non publié n'est jamais exigé. Les achats, candidatures et demandes continuent comme avant ; le bandeau cookies, lui, s'affiche dès le déploiement (et la mesure d'audience est désormais soumise à l'accord).
- Le bouton de publication est **refusé** si le texte contient « [À COMPLÉTER » ou si la validation du conseil (nom, date) manque.
- Passer un texte en vigueur le rend **immédiatement obligatoire** pour les nouveaux achats, candidatures et demandes, et pour les apprenants à leur prochaine page.
- Les candidatures **saisies à la main par l'équipe** n'ont pas de preuve d'acceptation (le candidat n'a rien coché) : la preuve est celle de l'étape suivante (paiement, espace apprenant).

## Ce qu'il faut renseigner avant publication (liste des champs « [À COMPLÉTER] »)
- **Mentions légales** : forme juridique, capital, RCCM, NINEA, directeur de la publication, hébergeur (raison sociale, adresse), droit applicable.
- **CGU** : droit applicable, juridiction compétente.
- **CGV** : forme juridique, NINEA/RCCM, régime de TVA (prix TTC ou HT), **conséquences d'un retard de paiement**, **politique de rétractation, d'annulation et de remboursement (par service : formations avant/après le début de session, simulateur, coaching)**, médiation de la consommation éventuelle, droit applicable, juridiction.
- **Confidentialité** : forme juridique, référent données, références légales exactes et formalités auprès de l'autorité sénégalaise de protection des données, **hébergeur**, **prestataire d'e-mails**, mécanisme de transfert hors pays, **durées de conservation** (candidatures, comptabilité, comptes, preuves, journaux), coordonnées de l'autorité.
- Vérifier la **liste des sous-traitants** (hébergeur, Cloudflare, PayDunya, e-mails, Google reCAPTCHA, Google Analytics) avec l'équipe technique.
- Supprimer la première ligne de chaque modèle (« SUPPRIMER CETTE LIGNE APRÈS VALIDATION… ») une fois le texte validé.

Les décisions commerciales à prendre en amont (remboursement, délais, rappels d'impayé, TVA) sont à arbitrer par vous : le code ne les devine pas.

## Mise en service (checklist)
1. Déployer (migration `V49__Legal_documents.sql` automatique ; les quatre brouillons sont créés au démarrage).
2. Avec un compte **Direction** : *Textes légaux*, compléter chaque texte, le faire valider par le conseil, puis **Publier** (nom du validateur, date, résumé).
3. Vérifier les pages publiques et le pied de page ; refaire un achat, une candidature, une demande de coaching et une connexion apprenant de test (parcours P93 à P107).
4. Conserver la liste des preuves : elle sert en cas de litige (durée de conservation à fixer avec le conseil).

## Données et sécurité
- La table `legal_acceptance` contient des données personnelles (e-mail, IP, navigateur) : elle est mentionnée dans la politique de confidentialité (finalité « preuve », base « intérêt légitime ») et n'est lisible que par la Direction.
- Une version publiée n'est jamais modifiée par le code ; l'empreinte permet de prouver quel texte exact a été accepté.
- Les routes publiques ne renvoient que le contenu des textes (jamais l'auteur de la publication ni le conseil).

## Limites connues (volontaires à ce stade)
- Pas encore de **réacceptation exigée pour une candidature ou un achat déjà faits** quand un texte change (la nouvelle version ne vaut que pour les actes suivants et pour les apprenants à leur connexion).
- Pas d'**export** des preuves (liste consultable seulement) ; à ajouter si votre conseil le demande.
- Les formulaires de **contact, liste d'attente, essai gratuit et newsletter** gardent leur case de consentement actuelle (non reliée aux textes versionnés).
- Pas de **réaffichage du bandeau** après un délai (par exemple 6 ou 13 mois) : le choix est conservé tant que le navigateur le garde.
