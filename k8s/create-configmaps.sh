#!/usr/bin/env bash
# Generates the 3 ConfigMaps that replace the bind-mounts from docker-compose.yml.
# Run from anywhere; it resolves paths relative to the repo root.
# Idempotent: re-run it after editing any of the source files to update the ConfigMap.
set -euo pipefail

# repo root = parent of this script's directory
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# nginx server config -> mounted at /etc/nginx/conf.d/default.conf in the client pod
kubectl create configmap nginx-config \
  --from-file=default.conf="$ROOT/client/nginx.conf" \
  --dry-run=client -o yaml | kubectl apply -f -

# static page -> mounted at /usr/share/nginx/html/index.html in the client pod
kubectl create configmap client-html \
  --from-file=index.html="$ROOT/client/index.html" \
  --dry-run=client -o yaml | kubectl apply -f -

# DB bootstrap script -> mounted at /docker-entrypoint-initdb.d/init-db.sh in the postgres pod
kubectl create configmap postgres-init \
  --from-file=init-db.sh="$ROOT/docker/init-db.sh" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "ConfigMaps applied: nginx-config, client-html, postgres-init"
