# Architecture

This document describes how **ecommerce-api** is structured, why layers are separated the way they are, and how to extend it safely. For stack overview, local run, and endpoint listings, see [README.md](./README.md). For environment variables and health probes in deployment, see [DEPLOYMENT.md](./DEPLOYMENT.md).

---

## High-level shape

The application is a **layered modular monolith**: HTTP adapters call **services** that orchestrate **repositories**, domain entities, **transactional outbox → Kafka** publication, and **Resilience4j** around flaky boundaries (payment gateway). Two HTTP stacks coexist:

| Stack | Base path | Role |
|-------|-----------|------|
| Spring MVC | `/api/v1/*` | Primary REST API |
| Jersey (JAX-RS) | `/jersey/*` | Parallel implementation for comparison |

Security, persistence, and business logic are shared; only the web layer differs.

---

## Package layout

| Area | Package / location | Responsibility |
|------|-------------------|----------------|
| Bootstrap | `com.example.ecommerce.EcommerceApplication` | Spring Boot entry; enables auditing, async, retry, scheduling |
| Web (MVC) | `controller/` | REST mapping, DTO binding, delegates to services |
| Web (errors) | `exception/GlobalExceptionHandler` | Maps exceptions to HTTP status + `ErrorResponse` |
| Domain | `domain/` | JPA entities, enums; mirrors Flyway schema |
| Application API | `service/` | Transactions, orchestration, rules |
| Persistence | `repository/` | Spring Data JPA (11 repositories) |
| Contracts | `dto/request`, `dto/response` | API payloads; keep entities off the wire |
| Security | `config/SecurityConfig`, `security/` | JWT filter chain, `UserDetails`, token validation |
| Payments | `payment/`, `payment/stripe/` | Gateway abstraction; mock or Stripe implementations |
| Messaging | `kafka/` | Outbox poller, producers, consumers, event DTOs (Jackson-serializable) |
| Ops | `actuator`, custom health indicators | Liveness/readiness, metrics |
| Schema | `src/main/resources/db/migration/` | Flyway versioned DDL |

---

## Layering rules

1. **Controllers stay thin** — Parse/validate input, call one service (or a small, obvious coordination), return DTOs. No business rules or direct multi-repository choreography.
2. **Services own use cases** — Example: `OrderService.checkout` sequences inventory reservation, order persistence, payment, cart clearing, outbox enqueue, and audit. Cross-cutting failures are handled here with compensating steps where modeled (e.g. release stock on payment failure).
3. **Repositories own persistence queries** — Custom finder methods and `@Query` live next to the aggregate they load; avoid leaking persistence concerns into controllers.
4. **DTOs at the boundary** — Request/response types isolate the REST contract from JPA entities (lazy loading, internal fields).
5. **Exceptions are intentional** — Domain/service code throws typed exceptions (`ResourceNotFoundException`, etc.); `GlobalExceptionHandler` translates them to stable HTTP semantics.

**Heuristic:** If logic appears in a controller or a controller reaches past its service into another feature’s repository, that is usually a layering leak.

---

## Transactions and consistency

- **`@Transactional` on service methods** defines unit-of-work boundaries (default propagation **`REQUIRED`** joins nested calls into one transaction).
- **`PaymentService.processPayment`** runs in the **caller's** transaction so `payments` rows referencing `orders` remain valid (same commit boundary). Using **`REQUIRES_NEW`** here would risk inserting a payment before the order is visible to the outer transaction and cause FK failures.
- **`InventoryService`** uses **`@Version`** optimistic locking on `Product` with **`@Retryable`** on `ObjectOptimisticLockingFailureException` to reduce oversell under concurrency.
- **Checkout idempotency** — `OrderService.checkout` keys off `idempotencyKey` so duplicate submits return the existing order instead of double-charging work.
- **Transactional outbox** — `OutboxService.enqueueOrderCreated` writes an `outbox_events` row in the **same transaction** as checkout. `OutboxPoller` (scheduled) reads pending rows and calls `OrderEventPublisher`, marking rows sent or incrementing retry on failure. This avoids publishing to Kafka before the order commit is durable.

**Heuristic:** Reserve **`REQUIRES_NEW`** for deliberate isolation (separate commit), not as a default for “nested helpers,” unless outbox/saga-style compensation is designed explicitly.

---

## Security rules

- **Stateless JWT** — CSRF disabled, `SessionCreationPolicy.STATELESS`, JWT validated in `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.
- **Authorization is layered:**
  - **HTTP matchers** in `SecurityConfig`: public auth and catalog reads, seller-only product URL mutations, admin routes and most actuator endpoints, public Stripe webhooks, then `anyRequest().authenticated()`.
  - **`@EnableMethodSecurity`** — Use **`@PreAuthorize`** when authorization depends on **resource ownership** or fine-grained rules beyond URL patterns (e.g. SELLER **or** ADMIN on product/category mutations).
- **Principal type** — The security context should expose a **`UserDetails`-compatible principal** (not a bare email string) so controllers and downstream code resolve the authenticated user consistently.

**Heuristic:** Coarse **roles** on paths; **method security** when the decision needs identity + entity ownership.

---

## Persistence and schema

- **PostgreSQL** in Docker/staging/production-style profiles (`application-docker.yml`); **H2** for the default `dev` profile (`application-dev.yml`).
- **Flyway** migrations `V1`–`V7`:
  - `V1` — customers, addresses
  - `V2` — products, categories
  - `V3` — orders, carts
  - `V4` — audit log
  - `V5` — password reset tokens
  - `V6` — transactional outbox (`outbox_events`)
  - `V7` — processed Stripe webhook events (`processed_webhook_events`)
- Treat SQL as the **source of truth** for tables and FKs; align `domain/` mappings and cascades with those constraints.

---

## Messaging and asynchronous work

- **Outbox → Kafka** — Checkout enqueues via `OutboxService`; `OutboxPoller` publishes to topic `orders.created` through `OrderEventPublisher`. Event DTOs (`OrderCreatedEvent` and nested types) stay Jackson-friendly (constructors/setters as needed for consumers).
- **`NotificationConsumer`** — Kafka listener on `orders.created`; currently logs a mock confirmation (production would wire a real email provider here). Password reset uses **`EmailService`** + `JavaMailSender` (MailHog in local Docker).
- **`AuditService`** uses **`@Async`** so audit writes do not block the request thread (failure modes should be understood in production—logging/monitoring matter).

---

## Payments

- **Provider selection** — `app.payment-gateway.provider`: `mock` (default) or `stripe`.
- **Mock** — `MockPaymentGatewayClient` + `PAYMENT_GATEWAY_URL`; health indicator may show DOWN when the mock URL is unreachable (expected locally).
- **Stripe** — `StripePaymentGatewayClient`, `StripeWebhookController`, and `StripeWebhookService` (conditional on `provider=stripe`). Webhook handling records processed event IDs in `processed_webhook_events` for idempotency. Configure `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` via env (see [DEPLOYMENT.md](./DEPLOYMENT.md)).

---

## Resilience (external-style boundaries)

- **`PaymentService`** applies **Resilience4j** (`@CircuitBreaker`, `@Retry`) around the payment gateway client (mock or Stripe).
- **Product search** uses a **rate limiter** instance (`productSearch`) in `application.yml`.
- Broader Resilience4j settings (circuit breaker windows, retry exceptions) live in `application.yml`.

---

## Observability

- **Spring Boot Actuator** — Health (including custom indicators such as payment gateway readiness), metrics, info. Liveness/readiness paths are used by Docker/Kubernetes as described in `DEPLOYMENT.md`.
- **Custom endpoints** — `InventoryEndpoint` at `/actuator/inventory` (admin-only via `SecurityConfig`).
- **Distinction:** Process **UP** vs dependency **DOWN** (e.g. gateway unhealthy) is reflected in health components; operators should treat readiness accordingly.

---

## Testing strategy

| Layer | Typical style | Purpose |
|-------|----------------|---------|
| Service | Mockito, `@ExtendWith(MockitoExtension.class)` | Business rules, branches, strict stubbing hygiene |
| Controller | `@WebMvcTest` | HTTP mapping, status codes, security wiring; mock services and security beans (`JwtTokenProvider`, etc.); `@WithMockUser` for authenticated scenarios |
| Repository | `@DataJpaTest` | Queries and mappings; use `flush`/`clear` when asserting DB-visible state vs first-level cache |
| Full stack | `@SpringBootTest` + integration tests | Critical paths (e.g. checkout) with full wiring |

**Heuristic:** Unit tests prove **rules**; slice tests prove **adapters** (web, JPA); a small set of integration tests proves **end-to-end** behavior.

---

## Adding a feature (vertical slice)

Preferred order for new behavior:

1. **Flyway migration** (only if the schema changes)
2. **Entity/repository** updates
3. **Service** method(s) with transaction boundary clear
4. **Controller** + request/response DTOs (and Jersey resource if the flow is mirrored)
5. **Tests** at service + controller (+ repository if new queries)

Keep changes scoped to the slice; avoid cross-feature repository calls from unrelated services without a deliberate boundary.

---

## Related diagrams

The ASCII architecture diagram in [README.md](./README.md) illustrates components end-to-end. If it disagrees with this document on **transaction propagation for payments** or **outbox vs direct Kafka publish**, treat **this file** as authoritative.
