#!/usr/bin/env bash
# Sauvegarde chiffree de la base PostgreSQL (conteneur Docker).
#
#   DB_CONTAINER=modeltech_prod_db DB_NAME=... DB_USER=... \
#   BACKUP_DIR=/var/backups/modeltech BACKUP_PASSPHRASE_FILE=/root/.modeltech-backup-passphrase \
#   bash scripts/backup/pg-backup.sh
#
# - format `pg_dump -Fc` (compresse, restaurable table par table), chiffre AES-256 (openssl, PBKDF2) ;
# - la phrase secrete est lue dans un FICHIER (chmod 600, hors du depot) : jamais en argument ni en variable ;
# - empreinte SHA-256 ecrite a cote ; le fichier produit est relu (dechiffre + liste) avant de conclure ;
# - les sauvegardes de plus de RETENTION_DAYS jours (14 par defaut) sont supprimees.
#
# Une sauvegarde qui reste sur le meme serveur ne protege pas d'une perte du serveur : copiez BACKUP_DIR
# vers un stockage externe chiffre (rclone vers un bucket S3 d'un autre fournisseur, par exemple),
# puis testez la restauration avec pg-restore-check.sh. Voir docs/ops/phase-0-securite.md.
set -euo pipefail

: "${DB_CONTAINER:?DB_CONTAINER manquant}"
: "${DB_NAME:?DB_NAME manquant}"
: "${DB_USER:?DB_USER manquant}"
: "${BACKUP_DIR:?BACKUP_DIR manquant}"
: "${BACKUP_PASSPHRASE_FILE:?BACKUP_PASSPHRASE_FILE manquant}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

[ -s "$BACKUP_PASSPHRASE_FILE" ] || { echo "Phrase secrete absente ou vide : $BACKUP_PASSPHRASE_FILE" >&2; exit 1; }

umask 077
mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/${DB_NAME}-${STAMP}.dump.enc"

docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner \
  | openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass "file:$BACKUP_PASSPHRASE_FILE" -out "$OUT.partial"
mv "$OUT.partial" "$OUT"

# Verification : le fichier doit se dechiffrer et se lister
TABLES=$(openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass "file:$BACKUP_PASSPHRASE_FILE" -in "$OUT" \
  | docker exec -i "$DB_CONTAINER" pg_restore --list | grep -c ' TABLE ' || true)
[ "$TABLES" -gt 0 ] || { echo "Sauvegarde illisible ou vide : $OUT" >&2; exit 1; }

( cd "$BACKUP_DIR" && sha256sum "$(basename "$OUT")" > "$(basename "$OUT").sha256" )

# Retention
find "$BACKUP_DIR" -maxdepth 1 -name "${DB_NAME}-*.dump.enc*" -mtime "+$RETENTION_DAYS" -delete

echo "Sauvegarde OK : $OUT ($(du -h "$OUT" | cut -f1), $TABLES entrees de table)"
