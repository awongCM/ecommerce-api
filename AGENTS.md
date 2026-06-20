# Agent guide — ecommerce-api

Short onboarding for humans and AI assistants working in this repository.

## Read first

- **[ARCHITECTURE.md](./ARCHITECTURE.md)** — Layering, transactions (especially checkout, payment, and outbox), security model, messaging, resilience, testing strategy, feature slice order.
- **[README.md](./README.md)** — Stack, local run (`docker-compose`), API surface, known gaps (e.g. admin bootstrap).
- **[DEPLOYMENT.md](./DEPLOYMENT.md)** — Health probes, env vars, Kubernetes/Docker notes.

## Stack (quick)

- Java **17**, Spring Boot **3.2**, Maven.
- Primary REST: Spring MVC under **`/api/v1`**.
- Parallel JAX-RS: Jersey under **`/jersey`** (same domain logic; do not let the two stacks drift unintentionally).
- JPA + **Flyway** (PostgreSQL in `docker` profile; H2 in `dev`), JWT security, Kafka (transactional outbox), Resilience4j, Actuator.
- Payments: **mock** (default) or **Stripe** (`app.payment-gateway.provider`).

## Build and test

```bash
mvn -q verify
```

Use `docker-compose up -d` when you need Postgres, Kafka, MailHog, or full integration behavior (see README).

## Package root

`com.example.ecommerce` — controllers, services, `domain/`, `repository/`, `dto/`, `config/`, `security/`, `payment/`, `kafka/`, `jersey/`, `actuator/`.

## Sensitive areas (read ARCHITECTURE before large edits)

- **Checkout** — `OrderService` idempotency, inventory optimistic locking / retries, payment in the **same** transaction as order creation, **outbox** for Kafka publish after commit.
- **Security** — `SecurityConfig`, JWT filter chain, `@PreAuthorize` vs URL matchers, actuator exposure, Stripe webhook path.
- **Schema** — `src/main/resources/db/migration/` is the source of truth (currently V1–V7); keep entities aligned with Flyway.

## Cursor

Project-specific agent rules live under **`.cursor/rules/`** (layering, persistence, security, messaging, tests). Prefer those rules plus this file over duplicating long sections of ARCHITECTURE.md.
