# Running Triasent on local Kubernetes

A local deployment for Docker Desktop's built-in Kubernetes. Because the cluster runs on
your Mac, there's no registry: the app images live in your local Docker and every app
Deployment uses `imagePullPolicy: Never`.

## Architecture

- **App services** (built locally with Spring Boot buildpacks): `userservice`,
  `authservice`, `messageservice`, `bff`, `botservice`, plus an nginx `client`.
- **Stateful infrastructure** (installed with Helm, *not* in `k8s/`):
  - **PostgreSQL × 2** — database-per-service via the Bitnami chart: `users-db`
    (`users_db`) for userservice, `messages-db` (`messages_db`) for messageservice.
  - **RabbitMQ** — Bitnami chart, used by messageservice (outbox relay) and botservice.
- **Credentials live in Kubernetes Secrets**, never in the repo. The Bitnami charts
  generate the DB/RabbitMQ passwords into Secrets; the apps read them via `secretKeyRef`.

## What's in `k8s/`

| Path | Contents |
|------|----------|
| `userservice.yaml` `authservice.yaml` `messageservice.yaml` `bff.yaml` `botservice.yaml` | app Deployment + Service each |
| `client.yaml` | nginx Deployment + Service (mounts the two ConfigMaps below) |
| `create-configmaps.sh` | generates the `nginx-config` + `client-html` ConfigMaps from `client/` |
| `gateway/httproute.yaml` | (optional) Traefik Gateway API route exposing the app at `http://localhost` |

Postgres and RabbitMQ are **not** here — they're Helm releases (see Deploy). `depends_on`
is dropped; apps retry until their dependencies are up. Service names match the `*_HOST`
env vars, and `fullnameOverride` keeps the chart Service names (`rabbitmq`) stable.

## Prerequisites

1. **Enable Kubernetes** in Docker Desktop → Settings → Kubernetes.
2. **Add the Bitnami Helm repo:**
   ```sh
   helm repo add bitnami https://charts.bitnami.com/bitnami && helm repo update
   ```
3. **Build the five app images** (each module is its own Maven build — no aggregator pom):
   ```sh
   for m in userservice authservice messageservice bff botservice; do (cd "$m" && mvn spring-boot:build-image -DskipTests); done
   ```
   Confirm: `docker images | grep 0.0.1-SNAPSHOT` lists all five. (nginx is a public image.)

## Deploy

### 1. Infrastructure (Helm) — creates the Secrets the apps consume

```sh
# Two Postgres instances. Passwords are auto-generated into Secrets (users-db / messages-db).
helm install users-db bitnami/postgresql -n default \
  --set fullnameOverride=users-db --set auth.username=users_app --set auth.database=users_db
helm install messages-db bitnami/postgresql -n default \
  --set fullnameOverride=messages-db --set auth.username=messages_app --set auth.database=messages_db

# RabbitMQ. NOTE: since Bitnami's Aug-2025 catalog change, the pinned rabbitmq image tag
# was moved to the `bitnamilegacy` repo, so point the image there.
helm install rabbitmq bitnami/rabbitmq -n default \
  --set fullnameOverride=rabbitmq --set auth.username=app \
  --set image.repository=bitnamilegacy/rabbitmq \
  --set global.security.allowInsecureImages=true
```
> If any Bitnami image 404s on pull (`not found`), it's the same catalog change — add
> `--set image.repository=bitnamilegacy/<name> --set global.security.allowInsecureImages=true`.

### 2. Application Secrets (values generated/sourced locally, never printed or committed)

```sh
# botservice LLM credentials, from botservice/.env
kubectl create secret generic llm-credentials --from-env-file=botservice/.env \
  --dry-run=client -o yaml | kubectl apply -f -

# OAuth client secrets, generated once and shared by the authorization server and its
# clients (authservice encodes them; bff/botservice present them).
kubectl create secret generic oauth-clients \
  --from-literal=gateway-client-secret="$(openssl rand -hex 24)" \
  --from-literal=bot-client-secret="$(openssl rand -hex 24)" \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 3. App ConfigMaps + manifests

```sh
bash k8s/create-configmaps.sh        # nginx-config + client-html
kubectl apply -f k8s/                # all app Deployments/Services (non-recursive: skips gateway/)
kubectl get pods                     # wait until everything is Running
```

### 4. (Optional) Expose the app via Traefik

If Traefik (with the Gateway API) is installed, route the app to `http://localhost`:
```sh
kubectl apply -f k8s/gateway/httproute.yaml
```

## Credentials / Secrets

| Secret | Created by | Consumed as |
|--------|-----------|-------------|
| `users-db` (key `password`) | postgresql chart | `SPRING_DATASOURCE_PASSWORD` in userservice |
| `messages-db` (key `password`) | postgresql chart | `SPRING_DATASOURCE_PASSWORD` in messageservice |
| `rabbitmq` (key `rabbitmq-password`) | rabbitmq chart | `SPRING_RABBITMQ_PASSWORD` in messageservice + botservice |
| `llm-credentials` | you (step 2) | `LLM_API_*` env in botservice |
| `oauth-clients` (keys `gateway-client-secret`, `bot-client-secret`) | you (step 2) | authservice encodes both (`GATEWAY_CLIENT_SECRET`/`BOT_CLIENT_SECRET`); bff + botservice present them as their `client-secret` |

Apps connect as least-privilege users (`users_app`, `messages_app`, `app`) — and **no
plaintext credentials (DB, RabbitMQ, or OAuth client secrets) exist in the repo**. To read
a generated value:
```sh
kubectl get secret rabbitmq -o jsonpath='{.data.rabbitmq-password}' | base64 -d
```

## Access from your Mac

The app is reachable two ways:
- **Via Traefik** (if installed): `http://localhost` directly.
- **Via port-forward:** `kubectl port-forward service/client 8090:80`.

**OAuth login** additionally needs the browser to reach authservice, using the
`127.0.0.1 authservice` entry in `/etc/hosts` plus:
```sh
kubectl port-forward service/authservice 9000:9000
```
Use port **8090** for login (the redirect URIs are configured for it). RabbitMQ UI:
`kubectl port-forward service/rabbitmq 15672:15672` (user `app`, password from the Secret).

## Teardown

```sh
# Apps + ConfigMaps + your Secrets
kubectl delete -f k8s/
kubectl delete configmap nginx-config client-html
kubectl delete secret llm-credentials oauth-clients

# Infrastructure (Helm) — this also removes the chart-generated Secrets
helm uninstall users-db messages-db rabbitmq -n default

# Wipe persistent data (StatefulSet PVCs are NOT deleted by helm uninstall)
kubectl delete pvc data-users-db-0 data-messages-db-0 data-rabbitmq-0
```

## Notes

- **Pause without data loss:** quit Docker Desktop (workloads + data return on reopen), or
  `kubectl scale deployment --all --replicas=0` and `helm`-managed StatefulSets stay put.
- **All credentials are Secret-managed:** DB, RabbitMQ, LLM, and OAuth client secrets are
  injected from Secrets — none are hardcoded in source or properties. authservice reads the
  raw client secrets from `oauth-clients` and BCrypt-encodes them; bff/botservice present
  the matching value from the same Secret.
- **Health checks:** dependency ordering relies on app retries; add
  `readinessProbe`/`livenessProbe` later for cleaner startup.
