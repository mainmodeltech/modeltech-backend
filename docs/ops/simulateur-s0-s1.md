# Simulateur de certification — lots S0 et S1

Référence : `docs/product/simulateur-pl-300.md` (dépôt front). Ce document décrit ce qui est livré côté serveur et ce qu'il faut savoir pour l'exploiter.

## S0 — Liste d'attente (migration V40)

- `POST /api/v1/simulator/waitlist` (public, limité par IP comme les autres formulaires) : prénom, e-mail, forfait souhaité (`ESSENTIEL`, `PRO`, `PRO_COACHING`, `ENTREPRISE`, `UNDECIDED`), échéance d'examen (`WITHIN_1_MONTH`, `WITHIN_3_MONTHS`, `LATER`, `UNDECIDED`), **accord de contact obligatoire** (`consent: true`).
- Stockée comme un message de contact de type `SIMULATOR_WAITLIST` : visible dans *Messages → Simulateur*, avec les notifications habituelles. Pas de nouvelle table.
- La page publique `/simulateur-pl-300` lit son contenu dans la clé `page.simulateur` (modifiable dans *Pages du site → Simulateur PL-300* ; valeur par défaut `site-content/page.simulateur.json`, jamais écrasée si elle existe).
- **Aucun paiement** n'est pris : les tarifs affichés sont **prévisionnels** et l'indiquent.

## S1 — Banque de questions (migration V41)

### Modèle
`sim_certification` → `sim_blueprint` (version du guide, durée, score de réussite) → `sim_blueprint_domain` → `sim_blueprint_skill` (cible de questions). Le blueprint **PL-300 du 20 avril 2026** est semé par la migration (4 domaines, 11 compétences, 400 questions visées).
`sim_item` (identité, code `PL300-MOD-001`) → `sim_item_version` (contenu : énoncé EN, `payload` JSON avec les choix et le corrigé, explication FR, statut).

### Règles garanties par le serveur **et** par la base
- Cycle `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → RETIRED`.
- **L'auteur ne relit pas et ne publie pas sa propre question.** Une approbation exige l'attestation de **vérification technique**.
- Une version **publiée ou retirée est figée** : déclencheur PostgreSQL `sim_item_version_frozen` (seul le passage `PUBLISHED → RETIRED` est permis). On corrige en ouvrant une **nouvelle version**.
- **Une seule version publiée par question** (index unique partiel) ; publier la version N retire automatiquement la précédente.
- Les corrigés ne sont servis que par `/api/v1/admin/simulator/**` ; l'API apprenant (lot S2) aura des types distincts, sans champ de correction.

### Droits (migration V41, profils Pédagogie et « Gestion complète »)
| Droit | Permet |
|---|---|
| `SIMULATOR_VIEW` | Lire la banque, le blueprint, la couverture |
| `SIMULATOR_AUTHOR` | Créer, modifier, soumettre, supprimer ses brouillons, ouvrir une nouvelle version |
| `SIMULATOR_REVIEW` | Approuver ou renvoyer la question d'un autre auteur |
| `SIMULATOR_MANAGE` | Publier, retirer, importer en lot |

### Audit
`SIM_ITEM_APPROVED`, `SIM_ITEM_PUBLISHED`, `SIM_ITEM_RETIRED`, `SIM_ITEMS_IMPORTED` (catégorie « Simulateur »).

### Import
`POST /api/v1/admin/simulator/items/import` : 200 questions au plus, chacune contrôlée avant écriture ; les invalides sont signalées (rang + motif) sans bloquer les autres ; tout arrive **en brouillon**.

## Mise en production
1. Déployer le backend (V40 puis V41 au démarrage). Aucune action manuelle.
2. Vérifier dans *Profils & droits* que Pédagogie porte les quatre droits `SIMULATOR_*` ; en attribuer au moins **deux personnes différentes** (auteur et relecteur) : sinon aucune question ne peut être publiée.
3. Contrôler *Pages du site → Simulateur PL-300* (textes et tarifs prévisionnels).
4. Surveiller *Messages → Simulateur* : c'est le tableau de bord de la porte de décision S0.
