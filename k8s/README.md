# Running Triasent on local Kubernetes

These manifests are a translation of `docker-compose.yml` for a **local** Kubernetes
cluster (Docker Desktop's built-in Kubernetes). Because the cluster runs on your own
Mac, there's no registry and no cross-architecture build needed — Kubernetes uses the
images already in your local Docker, which is why every app sets `imagePullPolicy: Never`.

## What's here

| File | Compose service(s) it replaces |
|------|--------------------------------|
| `postgres.yaml`       | `postgres` (a **StatefulSet** — its volume is managed via `volumeClaimTemplates`) |
| `rabbitmq.yaml`       | `rabbitmq` (a **StatefulSet** with a persistent volume for durable queues/messages) |
| `userservice.yaml`    | `userservice` |
| `authservice.yaml`    | `authservice` |
| `messageservice.yaml` | `messageservice` |
| `bff.yaml`            | `bff` |
| `client.yaml`         | `client` (nginx) |
| `botservice.yaml`     | `botservice` — LLM bot that consumes `message.published` and replies (**new**, no compose equivalent) |
| `create-configmaps.sh`| the three bind-mounted files (see below) |

`botservice` needs LLM API credentials, supplied via a Kubernetes **Secret** (`llm-credentials`)
rather than a ConfigMap — see Prerequisites and Deploy below.

Each file has a workload (**Deployment** = "run the container", or a **StatefulSet** for
Postgres since it owns persistent data) plus a **Service** ("give it a stable name other
pods reach it by"). The Service names match the `*_HOST` env vars, so the apps find each
other exactly like they did in compose. `depends_on` is dropped — Kubernetes starts
everything and the apps retry until their dependencies are up.

The three files that compose bind-mounted become **ConfigMaps**, generated from the real
files by `create-configmaps.sh` (single source of truth):
`client/nginx.conf`, `client/index.html`, `docker/init-db.sh`.

## Prerequisites

1. **Enable Kubernetes** in Docker Desktop → Settings → Kubernetes → *Enable Kubernetes*.
2. **Build the app images locally** so they exist in your Docker. Each module is its own
   Maven build, so build them from their own directories (no aggregator pom):
   ```sh
   for m in userservice authservice messageservice bff botservice; do (cd "$m" && mvn spring-boot:build-image -DskipTests); done
   ```
   Confirm: `docker images | grep 0.0.1-SNAPSHOT` lists all five.
   (Postgres, RabbitMQ, and nginx are public images — Kubernetes downloads those.)
3. **Provide botservice's LLM credentials** as a Secret, sourced from `botservice/.env`
   (`LLM_API_URL`, `LLM_API_KEY`, `LLM_API_MODEL`). Piped through `apply` so the values
   are never printed:
   ```sh
   kubectl create secret generic llm-credentials --from-env-file=botservice/.env \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

## Deploy

```sh
bash k8s/create-configmaps.sh   # create/refresh the 3 ConfigMaps
# create the llm-credentials Secret too (see Prerequisites step 3) if not done already
kubectl apply -f k8s/           # create everything else
kubectl get pods                # watch until all are Running
```

> botservice will keep restarting/erroring until the `llm-credentials` Secret exists, so
> create that Secret before (or right after) `kubectl apply -f k8s/`.

## Access it from your Mac

Unlike compose, ports aren't published to your machine automatically. Login needs **two**
forwards running at once (each blocks its terminal, so use two terminals or append `&`):

```sh
kubectl port-forward service/client      8090:80     # the app itself
kubectl port-forward service/authservice 9000:9000   # REQUIRED for OAuth login (see below)
```

Then open **http://localhost:8090**. Use port **8090** specifically — the OAuth redirect
URIs and the `X-Forwarded-Port 8090` header in `client/nginx.conf` are configured for it.
Complete the login + post-message flow just like in the compose setup.

### Why authservice must be forwarded too

The OAuth issuer is `http://authservice:9000` (see `bff` and `authservice`
`application.properties`), and you have `127.0.0.1 authservice` in `/etc/hosts`. During
login the BFF redirects your **browser** to `http://authservice:9000/oauth2/authorize…`,
so the browser — not just the cluster — must be able to reach authservice on 9000.
Compose published `9000:9000` automatically; in Kubernetes you forward it yourself.
Without this forward, login fails with connection-refused after the first redirect.

Optional — RabbitMQ management UI:
```sh
kubectl port-forward service/rabbitmq 15672:15672   # then http://localhost:15672
```

## Updating config

If you edit `client/nginx.conf`, `client/index.html`, or `docker/init-db.sh`, re-run
`bash k8s/create-configmaps.sh` and restart the affected pod
(`kubectl rollout restart deployment/client`). The Postgres init script only runs on a
**fresh** data volume, so changes to it require deleting the PVC `data-postgres-0` and
restarting Postgres (`kubectl rollout restart statefulset/postgres`).

## Pausing vs. tearing down

Everything here is declarative, so the cluster is disposable — recreate it any time with
`kubectl apply -f k8s/`. Pick based on whether you want to **keep the Postgres data**.

> ℹ️ The Postgres PVC (`data-postgres-0`) is created by the StatefulSet's
> `volumeClaimTemplates`, **not** by a file in `k8s/`. So `kubectl delete -f k8s/` does
> **not** delete it — your data survives a teardown. To actually wipe the DB you must
> delete the PVC explicitly (the default `hostpath` StorageClass reclaims it on delete).

**Pause and keep data** (e.g. coming back to it later):
```sh
# easiest: just quit Docker Desktop — workloads and data return on reopen.
# or, keep the cluster up but stop the pods:
kubectl scale deployment,statefulset --all --replicas=0   # resume with --replicas=1
```

**Tear down workloads, keep data** (re-`apply` later and the DB is still there):
```sh
kubectl delete -f k8s/          # removes Deployments, StatefulSet, Services (PVC stays)
kubectl delete configmap nginx-config client-html postgres-init   # the generated ConfigMaps
```

**Wipe persistent data too** (clean slate — Postgres tables *and* RabbitMQ queues/messages):
```sh
kubectl delete pvc data-postgres-0 data-rabbitmq-0
```

**Stop the port-forwards** when done:
```sh
pkill -f "kubectl port-forward"
```

## Notes / possible next steps

- **Credentials**: Postgres user/password are plaintext env here to mirror compose. For
  anything beyond local play, move them into a Kubernetes `Secret`.
- **Health checks**: dependency ordering currently relies on app retries. You can add
  `readinessProbe`/`livenessProbe` to each Deployment later for cleaner startup behavior.
