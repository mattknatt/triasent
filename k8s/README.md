# Running Triasent on local Kubernetes

These manifests are a translation of `docker-compose.yml` for a **local** Kubernetes
cluster (Docker Desktop's built-in Kubernetes). Because the cluster runs on your own
Mac, there's no registry and no cross-architecture build needed — Kubernetes uses the
images already in your local Docker, which is why every app sets `imagePullPolicy: Never`.

## What's here

| File | Compose service(s) it replaces |
|------|--------------------------------|
| `postgres.yaml`       | `postgres` (+ a PersistentVolumeClaim for the data volume) |
| `rabbitmq.yaml`       | `rabbitmq` |
| `userservice.yaml`    | `userservice` |
| `authservice.yaml`    | `authservice` |
| `messageservice.yaml` | `messageservice` |
| `bff.yaml`            | `bff` |
| `client.yaml`         | `client` (nginx) |
| `create-configmaps.sh`| the three bind-mounted files (see below) |

Each file has a **Deployment** ("run the container") and a **Service** ("give it a
stable name other pods reach it by"). The Service names match the `*_HOST` env vars,
so the apps find each other exactly like they did in compose. `depends_on` is dropped —
Kubernetes starts everything and the apps retry until their dependencies are up.

The three files that compose bind-mounted become **ConfigMaps**, generated from the real
files by `create-configmaps.sh` (single source of truth):
`client/nginx.conf`, `client/index.html`, `docker/init-db.sh`.

## Prerequisites

1. **Enable Kubernetes** in Docker Desktop → Settings → Kubernetes → *Enable Kubernetes*.
2. **Build the app images locally** so they exist in your Docker:
   ```sh
   mvn -pl userservice,authservice,messageservice,bff spring-boot:build-image
   ```
   Confirm: `docker images | grep 0.0.1-SNAPSHOT` lists all four.
   (Postgres, RabbitMQ, and nginx are public images — Kubernetes downloads those.)

## Deploy

```sh
bash k8s/create-configmaps.sh   # create/refresh the 3 ConfigMaps
kubectl apply -f k8s/           # create everything else
kubectl get pods                # watch until all are Running
```

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
**fresh** data volume, so changes to it require deleting the PVC (see teardown).

## Teardown

```sh
kubectl delete -f k8s/          # removes Deployments + Services (and the PVC definition)
# data may persist in the volume; to wipe the DB entirely:
kubectl delete pvc postgres-data
```

## Notes / possible next steps

- **Credentials**: Postgres user/password are plaintext env here to mirror compose. For
  anything beyond local play, move them into a Kubernetes `Secret`.
- **Health checks**: dependency ordering currently relies on app retries. You can add
  `readinessProbe`/`livenessProbe` to each Deployment later for cleaner startup behavior.
