#!/bin/sh
# Politique d'acces anonyme du bucket MinIO : LECTURE SEULE, uniquement sur les dossiers d'images publiques.
#
#   alumni/*              photos des alumni
#   partners/*            logos des partenaires
#   projects/screenshots/* et projects/covers/*   visuels des projets publies
#
# Tout le reste (justificatifs de paiement `payment-proofs/`, rendus d'apprenants `projects/<id-formation>/`...)
# reste prive : lecture par lien signe temporaire uniquement. Pas de liste du bucket, pas d'ecriture, pas de
# suppression en anonyme. `mc anonymous set public` ouvrait tout cela : ne plus l'utiliser.
#
# A executer ou `mc` est installe, alias deja configure :
#   sh apply-public-policy.sh <alias> <bucket>
# Depuis l'hote, via le conteneur MinIO (voir set-public.sh).
# ATTENTION : la meme liste existe dans docker-compose.staging.yml (service minio-init-staging) ; la garder alignee.
set -eu

ALIAS="${1:?alias mc}"
BUCKET="${2:?bucket}"
POLICY="/tmp/public-read-policy.json"

cat > "$POLICY" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {"AWS": ["*"]},
      "Action": ["s3:GetObject"],
      "Resource": [
        "arn:aws:s3:::${BUCKET}/alumni/*",
        "arn:aws:s3:::${BUCKET}/partners/*",
        "arn:aws:s3:::${BUCKET}/projects/screenshots/*",
        "arn:aws:s3:::${BUCKET}/projects/covers/*"
      ]
    }
  ]
}
EOF

mc anonymous set-json "$POLICY" "${ALIAS}/${BUCKET}"
echo "Politique appliquee a ${BUCKET} :"
mc anonymous get-json "${ALIAS}/${BUCKET}"
