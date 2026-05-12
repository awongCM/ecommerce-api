# Agent guide — ecommerce-api

Short onboarding for humans and AI assistants working in this repository.

## Read first

- **[ARCHITECTURE.md](./ARCHITECTURE.md)** — Layering, transactions (especially checkout and payment), security model, messaging, resilience, testing strategy, feature slice order.
- **[README.md](./README.md)** — Stack, local run (`docker-compose`), API surface, known gaps (e.g. admin bootstrap).
- **[DEPLOYMENT.md](./DEPLOYMENT.md)** — Health probes, env vars, Kubernetes/Docker notes.

## Stack (quick)

- Java **17**, Spring Boot **3.2**, Maven.
- Primary REST: Spring MVC under **`/api/v1`**.
- Parallel JAX-RS: Jersey under **`/jersey`** (same domain logic; do not let the two stacks drift unintentionally).
- JPA + **Flyway** (PostgreSQL in prod-style profiles; H2 in dev), JWT security, Kafka, Resilience4j, Actuator.

## Build and test

```bash
mvn -q verify
```

Use `docker-compose up -d` when you need Postgres, Kafka, or full integration behavior (see README).

## Package root

`com.example.ecommerce` — controllers, services, `domain/`, `repository/`, `dto/`, `config/`, `security/`, `kafka/`, `jersey/`, `actuator/`.

## Sensitive areas (read ARCHITECTURE before large edits)

- **Checkout** — `OrderService` idempotency, inventory optimistic locking / retries, payment in the **same** transaction as order creation where designed.
- **Security** — `SecurityConfig`, JWT filter chain, `@PreAuthorize` vs URL matchers, actuator exposure.
- **Schema** — `src/main/resources/db/migration/` is the source of truth; keep entities aligned with Flyway.

## Cursor

Project-specific agent rules live under **`.cursor/rules/`** (layering, persistence, security, messaging, tests). Prefer those rules plus this file over duplicating long sections of ARCHITECTURE.md.
