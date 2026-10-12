#!/usr/bin/env bash
# Test de restauration : restaure une sauvegarde chiffree dans une base TEMPORAIRE (restore_check_*),
# controle son contenu, puis supprime cette base temporaire. N'ecrit jamais dans la base de production.
#
#   DB_CONTAINER=modeltech_prod_db DB_USER=... BACKUP_PASSPHRASE_FILE=/root/.modeltech-backup-passphrase \
#   bash scripts/backup/pg-restore-check.sh /var/backups/modeltech/<fichier>.dump.enc
#
# A faire apres chaque changement du procede de sauvegarde, et au moins une fois par trimestre.
set -euo pipefail

: "${DB_CONTAINER:?DB_CONTAINER manquant}"
: "${DB_USER:?DB_USER manquant}"
: "${BACKUP_PASSPHRASE_FILE:?BACKUP_PASSPHRASE_FILE manquant}"
FILE="${1:?fichier .dump.enc a tester}"

[ -f "$FILE" ] || { echo "Fichier introuvable : $FILE" >&2; exit 1; }
if [ -f "$FILE.sha256" ]; then
  ( cd "$(dirname "$FILE")" && sha256sum -c "$(basename "$FILE").sha256" )
fi

CHECK_DB="restore_check_$(date +%s)"
case "$CHECK_DB" in restore_check_*) ;; *) echo "Nom de base inattendu" >&2; exit 1;; esac

cleanup() { docker exec "$DB_CONTAINER" dropdb -U "$DB_USER" --if-exists "$CHECK_DB" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker exec "$DB_CONTAINER" createdb -U "$DB_USER" "$CHECK_DB"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass "file:$BACKUP_PASSPHRASE_FILE" -in "$FILE" \
  | docker exec -i "$DB_CONTAINER" pg_restore -U "$DB_USER" -d "$CHECK_DB" --no-owner --exit-on-error

q() { docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$CHECK_DB" -tA -c "$1"; }
TABLES=$(q "select count(*) from information_schema.tables where table_schema='public'")
MIGRATION=$(q "select coalesce(max(version::int),0) from flyway_schema_history where success and version ~ '^[0-9]+$'")
echo "Restauration OK : $TABLES tables, derniere migration Flyway V$MIGRATION"
for t in admin_users learners registrations payments; do
  if [ "$(q "select to_regclass('public.$t') is not null")" = "t" ]; then
    echo "  $t : $(q "select count(*) from $t") ligne(s)"
  fi
done
[ "$TABLES" -gt 0 ] && [ "$MIGRATION" -gt 0 ] || { echo "Restauration incomplete" >&2; exit 1; }
echo "Base temporaire $CHECK_DB supprimee a la sortie."
