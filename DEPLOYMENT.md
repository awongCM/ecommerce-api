# Environment & Deployment Guide

## Environment Variable Model

This project follows the [12-factor app](https://12factor.net/config) principle: **config that changes between environments is injected via environment variables**, never hardcoded.

---

## Environments

| Environment | Purpose | Spring profile | Database | Payment gateway |
|---|---|---|---|---|
| **Local (Maven)** | `mvn spring-boot:run` on host | `dev` (default) | H2 in-memory | Mock (default); health may show DOWN |
| **Local (Docker)** | `docker-compose up` | `docker` | PostgreSQL container | Mock (default); health may show DOWN |
| **Staging / UAT** | Integration & QA | `docker` | Managed PostgreSQL | Mock URL or Stripe sandbox |
| **Production** | Live traffic | `docker` (+ optional local `application-prod.yml`, gitignored) | Managed PostgreSQL | Stripe or live gateway URL |

There is **no committed `application-prod.yml`**. Production-style deployments use the **`docker` profile** with env vars (or a gitignored local overlay). See `.gitignore`.

---

## Key Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `dev` | Active Spring profile (`dev`, `docker`) |
| `SPRING_DATASOURCE_URL` | Yes (non-dev) | H2 in-memory | JDBC URL for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Yes (non-dev) | — | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Yes (non-dev) | — | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Yes (non-dev) | `localhost:9092` | Kafka broker address |
| `JWT_SECRET` | Yes (all) | Dev placeholder | Secret key for signing JWT tokens — **must be changed in prod** |
| `PAYMENT_GATEWAY_PROVIDER` | No | `mock` | `mock` or `stripe` |
| `PAYMENT_GATEWAY_URL` | Mock deployments | `https://api.mock-payment.com` | Base URL for mock gateway health checks |
| `PAYMENT_CURRENCY` | No | `usd` | Currency for Stripe PaymentIntents |
| `STRIPE_SECRET_KEY` | Stripe | — | Stripe API secret key |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhooks | — | Stripe webhook signing secret |
| `MAIL_FROM` | No | `noreply@ecommerce.example.com` | From address for password reset emails |
| `APP_BASE_URL` | No | `http://localhost:3000` | Frontend base URL embedded in reset links |
| `MAIL_HOST` | Docker | `mailhog` | SMTP host (MailHog in docker-compose) |
| `MAIL_PORT` | Docker | `1025` | SMTP port |

---

## Local Development

### Docker Compose (recommended)

No env vars needed. Just run:

```bash
docker-compose up -d
```

The `docker` Spring profile is activated automatically via `docker-compose.yml`. PostgreSQL, Kafka, and MailHog run as containers. The payment gateway health indicator will show `DOWN` with the default mock provider — this is expected and does not affect the app.

Health checks:
- Liveness: http://localhost:8080/actuator/health/liveness
- Readiness: http://localhost:8080/actuator/health/readiness
- Full detail: http://localhost:8080/actuator/health

### Maven on host (dev profile)

Uses H2 and expects Kafka on `localhost:9092` (and MailHog on port 1025 for password reset emails if you run it separately):

```bash
mvn spring-boot:run
```

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
PAYMENT_GATEWAY_PROVIDER=stripe
STRIPE_SECRET_KEY=<from-secrets-manager>
STRIPE_WEBHOOK_SECRET=<from-secrets-manager>
PAYMENT_CURRENCY=usd
APP_BASE_URL=https://staging.example.com
MAIL_FROM=noreply@staging.example.com
```

For mock gateway testing instead of Stripe, omit Stripe vars and set `PAYMENT_GATEWAY_URL` to your sandbox/mock URL.

---

## Production (Kubernetes example)

Use the **`docker` profile** with standard Spring datasource env vars (matches `application-docker.yml`):

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
  - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: kafka-bootstrap-servers
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: jwt-secret
  - name: PAYMENT_GATEWAY_PROVIDER
    value: stripe
  - name: STRIPE_SECRET_KEY
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: stripe-secret-key
  - name: STRIPE_WEBHOOK_SECRET
    valueFrom:
      secretKeyRef:
        name: ecommerce-secrets
        key: stripe-webhook-secret
  - name: APP_BASE_URL
    value: https://shop.example.com
  - name: MAIL_FROM
    value: noreply@shop.example.com
```

See also [k8s/deployment.yaml](./k8s/deployment.yaml) for probe and resource settings.

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
