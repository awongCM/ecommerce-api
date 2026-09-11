# Agent guide — ecommerce-api

Short onboarding for humans and AI assistants working in this repository.

## Read first

- **[ARCHITECTURE.md](./ARCHITECTURE.md)** — Layering, transactions (especially checkout, payment, and outbox), security model, messaging, resilience, testing strategy, feature slice order.
- **[README.md](./README.md)** — Stack, diagrams, local run, API surface. Known gaps: capstone metrics not measured; native/Jersey/AI unverified. First admin: Flyway seed in `dev`; `ADMIN_EMAIL`/`ADMIN_PASSWORD` in docker.
- **[DEPLOYMENT.md](./DEPLOYMENT.md)** — Health probes, env vars, Kubernetes/Docker notes.

## Stack (quick)

- Java **25** (Eclipse Temurin 25), Spring Boot **3.5**, Maven. Preview features require `--enable-preview` for `java -jar` (Dockerfile `ENTRYPOINT` includes it; `spring-boot-maven-plugin` sets it for `mvn spring-boot:run`).
- Primary REST: Spring MVC under **`/api/v1`**.
- Parallel JAX-RS: Jersey under **`/jersey`** (same domain logic; do not let the two stacks drift unintentionally).
- JPA + **Flyway** (PostgreSQL in `docker` profile; H2 in `dev`), JWT security, Kafka (transactional outbox), Resilience4j, Actuator.
- Payments: **mock** (default) or **Stripe** (`app.payment-gateway.provider`).
- Optional Spring AI order-anomaly triage (post-commit Kafka only; `ORDER_ANOMALY_TRIAGE_ENABLED` default off).

## Build and test

```bash
mvn -q verify
```

Use `docker-compose up -d` when you need Postgres, Kafka, MailHog, or full integration behavior (see README).

## Package root

`com.example.ecommerce` — controllers, services, `domain/`, `repository/`, `dto/`, `config/`, `security/`, `payment/`, `kafka/`, `jersey/`, `actuator/`.

## Sensitive areas (read ARCHITECTURE before large edits)

- **Checkout** — `OrderService` idempotency, inventory optimistic locking / retries, payment in the **same** transaction as order creation, **outbox** for Kafka publish after commit. Do not put LLM or AI calls on this path.
- **Security** — `SecurityConfig`, JWT filter chain, `@PreAuthorize` vs URL matchers, actuator exposure, Stripe webhook path.
- **Schema** — `src/main/resources/db/migration/` is the source of truth (currently V1–V8); keep entities aligned with Flyway. Next unused shared version is **V9**. Dev-only Flyway lives in `classpath:db/dev` (local admin seed).
- **AI triage** — `OrderAnomalyTriageConsumer` / `OrderAnomalyTriageService` only; flag default off; LLM outside DB TX; docker fail-fast without `SPRING_AI_OPENAI_API_KEY`.

## Cursor

Project-specific agent rules live under **`.cursor/rules/`** (layering, persistence, security, messaging, tests). Prefer those rules plus this file over duplicating long sections of ARCHITECTURE.md.
