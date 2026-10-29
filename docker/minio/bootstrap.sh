#!/bin/sh
set -eu
attempt=0
until mc alias set bootstrap http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1 \
  && mc ready bootstrap >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  [ "$attempt" -lt 30 ] || exit 1
  sleep 2
done
mc mb --ignore-existing bootstrap/cadence-evidence >/dev/null
mc anonymous set none bootstrap/cadence-evidence >/dev/null
mc admin user add bootstrap "$EVIDENCE_ACCESS_KEY" "$EVIDENCE_SECRET_KEY" >/dev/null
mc admin policy create bootstrap cadence-evidence-runtime /runtime-policy.json >/dev/null
mc admin policy attach bootstrap cadence-evidence-runtime --user "$EVIDENCE_ACCESS_KEY" >/dev/null
echo "Private local evidence bucket and restricted runtime identity initialized"
