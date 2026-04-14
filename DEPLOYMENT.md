# Environment & Deployment Guide

## Environment Variable Model

This project follows the [12-factor app](https://12factor.net/config) principle: **config that changes between environments is injected via environment variables**, never hardcoded.

---

## Environments

| Environment | Purpose | Payment Gateway |
|---|---|---|
| **Local dev** | Development on your machine via `docker-compose` | Not set — mock URL fallback used, health indicator expected to be DOWN |
| **Staging / UAT** | Integration & QA testing | Sandbox URL from payment provider (test keys, no real money) |
| **Production** | Live traffic | Live gateway URL injected via secrets manager |

---

## Key Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `dev` | Active Spring profile (`dev`, `docker`, `prod`) |
| `SPRING_DATASOURCE_URL` | Yes (non-dev) | H2 in-memory | JDBC URL for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Yes (non-dev) | — | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Yes (non-dev) | — | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Yes (non-dev) | `localhost:9092` | Kafka broker address |
| `JWT_SECRET` | Yes (all) | Dev placeholder | Secret key for signing JWT tokens — **must be changed in prod** |
| `PAYMENT_GATEWAY_URL` | Staging + Prod | `https://api.mock-payment.com` | Base URL for the payment gateway |

---

## Local Development

No env vars needed. Just run:

```bash
docker-compose up -d
```

The `docker` Spring profile is activated automatically via `docker-compose.yml`. PostgreSQL and Kafka run as containers. The payment gateway health indicator will show `DOWN` — this is expected and does not affect the app.

Health checks:
- Liveness: http://localhost:8080/actuator/health/liveness
- Readiness: http://localhost:8080/actuator/health/readiness
- Full detail: http://localhost:8080/actuator/health

---

## Staging

Set these in your CI/CD pipeline or container orchestration secrets:

```bash
SPRING_PROFILES_ACTIVE=docker
SPRING_DATASOURCE_URL=jdbc:postgresql://<staging-db-host>:5432/ecommercedb
SPRING_DATASOURCE_USERNAME=<from-secrets-manager>
SPRING_DATASOURCE_PASSWORD=<from-secrets-manager>
SPRING_KAFKA_BOOTSTRAP_SERVERS=<staging-kafka-host>:9092
JWT_SECRET=<from-secrets-manager>
PAYMENT_GATEWAY_URL=https://sandbox.your-payment-provider.com
```

---

## Production (Kubernetes example)

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: docker
  - name: SPRING_DATASOURCE_URL
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: datasource-url
  - name: SPRING_DATASOURCE_USERNAME
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: datasource-username
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: datasource-password
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: jwt-secret
  - name: PAYMENT_GATEWAY_URL
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: payment-gateway-url
```

---

## Health Check Design

The app exposes three health endpoints:

| Endpoint | What it checks | Used by |
|---|---|---|
| `/actuator/health/liveness` | Is the JVM alive? (`ping`, `diskSpace`) | Docker / Kubernetes liveness probe |
| `/actuator/health/readiness` | Can it serve traffic? (`ping`, `db`, `diskSpace`) | Kubernetes readiness probe |
| `/actuator/health` | Full detail including all indicators | Monitoring / dashboards |

The `paymentGateway` indicator is intentionally **excluded from liveness and readiness** — an unreachable payment gateway should not take down the pod. It is visible in the full health endpoint for observability.

---

## Secrets Management — Rules

1. **Never commit secrets** to source control (`.env` files with real values, JWT secrets, DB passwords).
2. **Never hardcode** credentials in `application.yml` or `application-docker.yml`.
3. Use the `${VAR:default}` pattern only for **non-sensitive defaults** (e.g. ports, feature flags). Sensitive vars should have **no default** so startup fails fast if they're missing in prod.
4. Rotate `JWT_SECRET` between environments — a token signed in staging must not be valid in production.
