# Kubernetes guide — local learning & cloud deployment

Hands-on path for running **ecommerce-api** on Kubernetes: learn on Colima, then ship to your own cloud cluster. Focused on **practical advantages** over `docker compose` or a single VM.

---

## Quick reference

| Environment | Manifests | Deploy |
|-------------|-----------|--------|
| **Local (Colima + k3s)** | [`k8s/local/`](./local/) | `./scripts/k8s-local-up.sh` |
| **Production-shaped** | [`k8s/deployment.yaml`](./deployment.yaml) | Registry + Secrets + `kubectl apply` |

Tear down local stack:

```bash
./scripts/k8s-local-down.sh
```

Reach the API locally:

```bash
kubectl -n ecommerce-local port-forward svc/ecommerce-api 8080:8080
```

- Readiness: http://localhost:8080/actuator/health/readiness
- Swagger: http://localhost:8080/swagger-ui.html

---

## When Kubernetes is worth it

| Compose / single server | Kubernetes |
|-------------------------|------------|
| One machine, `docker compose up` | Many nodes; scheduler places workloads |
| Restart = SSH and fix manually | **Self-healing**: dead pod replaced automatically |
| New version = stop/start containers | **Rolling updates**: old pods drain while new ones pass readiness |
| Scale = bigger VM or manual duplicate | **Scale replicas** with one command |
| “Is it healthy?” = check logs yourself | **Probes** gate traffic; bad deploys don't receive requests |
| Config lives in compose on one host | **Declarative desired state** + Secrets / ConfigMaps |
| Fine for one app, one environment | Same manifests for **local → staging → prod** |

**Kubernetes is overkill** if you only ever run one small API on one box.

**Kubernetes pays off** when you care about: uptime without manual babysitting, safe deploys, horizontal scale, and running several services the same way everywhere.

This app is a good teacher: **DB readiness**, **Kafka**, **JWT secrets**, and **Actuator probes** are exactly what K8s orchestrates well.

---

## Phase 1 — Learn Kubernetes locally (Colima + `k8s/local`)

**Goal:** Experience platform behaviors that Compose doesn't surface.

### Prerequisites

```bash
colima start --kubernetes --cpu 4 --memory 6
./scripts/k8s-local-up.sh
```

Colima with **Docker runtime** shares images with k3s — no manual `ctr import` needed after `docker build`.

---

### 1.1 Map what you already have

```bash
kubectl -n ecommerce-local get all
kubectl -n ecommerce-local describe deployment ecommerce-api
kubectl -n ecommerce-local get endpoints ecommerce-api
```

**Takeaway:** **Pod** = running container(s). **Service** = stable DNS (`postgres`, `kafka`, `ecommerce-api`). **Deployment** = “keep N copies of this pod spec.”

---

### 1.2 Self-healing

```bash
kubectl -n ecommerce-local get pods -l app=ecommerce-api
kubectl -n ecommerce-local delete pod -l app=ecommerce-api
kubectl -n ecommerce-local get pods -w
```

**Takeaway:** You didn't restart anything — the Deployment recreated the pod. That's the core K8s control loop.

---

### 1.3 Probes and safe traffic

```bash
kubectl -n ecommerce-local port-forward svc/ecommerce-api 8080:8080
curl -s http://localhost:8080/actuator/health/readiness
```

**Experiment:** Temporarily set a wrong readiness probe path in [`k8s/local/app.yaml`](./local/app.yaml), then:

```bash
kubectl apply -k k8s/local
kubectl -n ecommerce-local get pods -w
```

Watch the pod stay **Running** but **not Ready**. Fix the probe and re-apply.

**Takeaway:** Liveness ≠ readiness. Unready pods don't get Service traffic. Your `/actuator/health/readiness` (includes DB check) is doing real work.

---

### 1.4 Rolling update (the big one)

```bash
docker build -t ecommerce-api:v2 .
kubectl -n ecommerce-local set image deployment/ecommerce-api \
  ecommerce-api=ecommerce-api:v2
kubectl -n ecommerce-local rollout status deployment/ecommerce-api
kubectl -n ecommerce-local rollout history deployment/ecommerce-api
```

**Bad deploy practice:**

```bash
kubectl -n ecommerce-local set image deployment/ecommerce-api \
  ecommerce-api=ecommerce-api:does-not-exist
kubectl -n ecommerce-local rollout status deployment/ecommerce-api   # fails
kubectl -n ecommerce-local rollout undo deployment/ecommerce-api
```

**Takeaway:** Deploy is a **controlled rollout**, not `docker stop && docker start`. Rollback is one command.

---

### 1.5 Horizontal scale

```bash
kubectl -n ecommerce-local scale deployment/ecommerce-api --replicas=3
kubectl -n ecommerce-local get pods -l app=ecommerce-api
```

Port-forward and hit the API — traffic is spread across ready pods via the Service.

**Takeaway:** Scale is declarative. Compose can scale some services; K8s makes replica count + health + load balancing first-class.

---

### 1.6 Config without rebuilding the image

Move `JWT_SECRET` (or similar) from inline env in [`k8s/local/app.yaml`](./local/app.yaml) into a Secret, reference it with `envFrom` or `secretKeyRef`, then:

```bash
kubectl apply -k k8s/local
kubectl -n ecommerce-local rollout restart deployment/ecommerce-api
```

**Takeaway:** **Image** = code. **Secrets / ConfigMaps** = environment. That split is how production works.

---

### 1.7 Observe failure modes

```bash
kubectl -n ecommerce-local logs -f deployment/ecommerce-api
kubectl -n ecommerce-local delete pod -l app=postgres
kubectl top pods -n ecommerce-local    # if metrics-server is available
```

Watch the init container wait for Postgres, then the app comes up.

---

### Local graduation exercise

Add **Ingress** (nginx) so you don't need `port-forward` — same object you'll use in the cloud.

---

## Phase 2 — Ship to your own cloud cluster

**Goal:** Same mental model; real registry, managed services, public URL.

### 2.1 Recommended stack

| Piece | Recommendation |
|-------|----------------|
| Cluster | DigitalOcean Kubernetes, Civo, Linode LKE, or GKE Autopilot |
| Registry | GitHub Container Registry (`ghcr.io/<you>/ecommerce-api`) |
| Database | **Managed Postgres** (not in-cluster for production) |
| Kafka | Managed Kafka / Confluent Cloud; portfolio OK with in-cluster Redpanda |
| Ingress + TLS | nginx-ingress + cert-manager (Let's Encrypt) |

Avoid running Postgres on cluster disks in production — backups and failover are why managed DB exists.

---

### 2.2 Cloud checklist (in order)

1. **Create cluster** (1–2 nodes is enough to start).
2. **Install ingress controller** (Helm or provider add-on).
3. **Create registry pull secret** if using private GHCR images.
4. **Build and push image:**

   ```bash
   docker build -t ghcr.io/<you>/ecommerce-api:1.0.0 .
   docker push ghcr.io/<you>/ecommerce-api:1.0.0
   ```

5. **Create namespace and secrets** (keys match [`k8s/deployment.yaml`](./deployment.yaml)):

   - `datasource-url` — full JDBC URL to managed Postgres
   - `datasource-username`
   - `datasource-password`
   - `kafka-bootstrap-servers`
   - `jwt-secret`
   - `stripe-secret-key`, `stripe-webhook-secret` (if using Stripe)

   Example:

   ```bash
   kubectl create namespace ecommerce

   kubectl create secret generic ecommerce-secrets -n ecommerce \
     --from-literal=datasource-url='jdbc:postgresql://<host>:5432/ecommercedb' \
     --from-literal=datasource-username='...' \
     --from-literal=datasource-password='...' \
     --from-literal=kafka-bootstrap-servers='...' \
     --from-literal=jwt-secret='...'
   ```

6. **Update image** in `k8s/deployment.yaml`:

   ```yaml
   image: ghcr.io/<you>/ecommerce-api:1.0.0
   imagePullPolicy: Always
   ```

7. **Deploy:**

   ```bash
   kubectl apply -f k8s/deployment.yaml -n ecommerce
   kubectl -n ecommerce rollout status deployment/ecommerce-api
   ```

8. **Add Ingress** — host `api.yourdomain.com` → `ecommerce-api-service:80`.
9. **Verify probes** — only ready pods receive traffic.
10. **CI/CD** — GitHub Action on push: build → push GHCR → `kubectl set image` or `kubectl apply -k k8s/prod`.

See also [DEPLOYMENT.md](../DEPLOYMENT.md) for environment variables and health probe design.

---

### 2.3 What changes from local to cloud

| Local (`k8s/local`) | Cloud (`k8s/deployment.yaml`) |
|---------------------|-------------------------------|
| `ecommerce-api:local` | Image from GHCR / ECR / etc. |
| In-cluster Postgres + Redpanda | Managed Postgres + managed Kafka |
| `ClusterIP` + port-forward | LoadBalancer or Ingress + TLS |
| Inline dev secrets | Kubernetes Secrets (or external secrets manager) |
| 1 replica | 2–3+ replicas for rolling updates |

---

## Phase 3 — Full practical advantage demo

Run this narrative once — it's the story for interviews or a blog post:

1. **Baseline:** 3 API replicas behind a Service, health green.
2. **Deploy v2** with a deliberate bug → readiness fails → **no user impact** on healthy pods.
3. **`rollout undo`** → back to good version in seconds.
4. **Scale to 5** under load (use `hey`, `ab`, or similar).
5. **Delete random pods** → count returns to desired replicas.
6. **Rotate JWT secret** via Secret + rolling restart.

Compose can do pieces of this. Kubernetes makes it **standard, repeatable, and operable without SSH**.

---

## Suggested repo evolution

| Step | Add to repo |
|------|-------------|
| Local | `k8s/local/ingress.yaml` |
| Cloud | Kustomize overlays (`base` + `local` + `prod`) |
| Ops | GitHub Action: build → push GHCR → deploy |
| Docs | Provider-specific runbook appendix (DO / AWS / GCP) |

---

## One-week plan

| Day | Focus |
|-----|--------|
| **1–2** | Local exercises 1.2–1.5 — delete pods, rolling update, rollback, scale |
| **3** | Move secrets out of inline env; read `kubectl describe pod` Events |
| **4–5** | Smallest managed cluster + GHCR + managed Postgres; deploy `k8s/deployment.yaml` |
| **6** | Break a deploy on purpose; `rollout undo`; write 5 bullets on what happened |

---

## When to use what

| Tool | Use when |
|------|----------|
| **`docker compose up`** | Fastest local dev; full stack (MailHog, Kafka UI, etc.) |
| **`k8s/local` + Colima** | Learning K8s or testing manifests |
| **`k8s/deployment.yaml`** | Staging / production on a real cluster |

---

## Related files

- [`k8s/local/`](./local/) — namespace, Postgres, Kafka (Redpanda), app, Kustomization
- [`k8s/deployment.yaml`](./deployment.yaml) — production-shaped Deployment + LoadBalancer Service
- [`scripts/k8s-local-up.sh`](../scripts/k8s-local-up.sh) — build and deploy local stack
- [`scripts/k8s-local-down.sh`](../scripts/k8s-local-down.sh) — tear down local stack
- [`DEPLOYMENT.md`](../DEPLOYMENT.md) — env vars, profiles, health checks
