# ecommerce-api

A production-grade RESTful ecommerce API built with Spring Boot 3.5.

Layering, transactions, and testing: **[ARCHITECTURE.md](./ARCHITECTURE.md)**. Env vars and probes: **[DEPLOYMENT.md](./DEPLOYMENT.md)**. Agent onboarding: **[AGENTS.md](./AGENTS.md)**.

## Tech Stack
- Java 25 (Eclipse Temurin 25), Spring Boot 3.5
- Preview features enabled at compile/test time (`--enable-preview` in `pom.xml`); JVM `java -jar` launches need the same flag (included in the Dockerfile `ENTRYPOINT`)
- Spring Security + JWT authentication
- Spring Data JPA + PostgreSQL + Flyway (V1–V8)
- Apache Kafka (transactional outbox → `orders.created`)
- Resilience4j (circuit breaker, retry, rate limiter)
- Spring AI 1.1.x — optional post-commit order anomaly triage (feature-flagged; stub model in `dev`)
- Jersey (JAX-RS) — parallel implementation for comparison
- Docker (Temurin 25 JRE + ZGC default; optional GraalVM native stage) + Kubernetes-ready ([`k8s/`](./k8s/))

## Architecture

Design heuristics (layering, transactions, security, testing) live in [ARCHITECTURE.md](./ARCHITECTURE.md). If this diagram disagrees with ARCHITECTURE on payment TX or outbox, treat **ARCHITECTURE.md** as authoritative.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                        │
│         Browser / Mobile App / Postman / cURL                               │
└──────────────┬──────────────────────────────────┬───────────────────────────┘
               │                                  │
               ▼                                  ▼
┌──────────────────────────┐       ┌──────────────────────────┐
│   Spring MVC (REST)      │       │   Jersey (JAX-RS)        │
│   /api/v1/*              │       │   /jersey/*              │
│                          │       │                          │
│  ┌────────────────────┐  │       │  ┌────────────────────┐  │
│  │ AuthController     │  │       │  │ ProductResource    │  │
│  │ ProductController  │  │       │  │ OrderResource      │  │
│  │ CategoryController │  │       │  │                    │  │
│  │ AddressController  │  │       │  │ JerseyAuthFilter   │  │
│  │ CartController     │  │       │  │ JerseyExceptionMap │  │
│  │ OrderController    │  │       │  │                    │  │
│  │ AdminController    │  │       │  │                    │  │
│  │ StripeWebhookCtrl  │  │       │  │                    │  │
│  └────────────────────┘  │       │  └────────────────────┘  │
│  GlobalExceptionHandler  │       │                          │
└──────────┬───────────────┘       └──────────┬───────────────┘
           │                                  │
           ▼                                  ▼
┌─────────────────────────────────────────────────────────────┐
│                     SECURITY LAYER                           │
│                                                              │
│  JwtAuthenticationFilter ──► JwtTokenProvider                │
│  UserDetailsServiceImpl      SecurityConfig                  │
│  BCrypt(12) password encoding                                │
│  Stateless JWT (jjwt 0.12.3)                                │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     SERVICE LAYER                            │
│                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │ AuthService │  │ProductService│  │   CartService      │  │
│  └─────────────┘  └──────────────┘  └────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  OrderService                         │   │
│  │  (checkout orchestrator: inventory → payment → cart   │   │
│  │   → outbox enqueue → after-commit STS audit/notify)   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐   │
│  │InventoryService│  │ PaymentService │  │ AuditService │   │
│  │ @Retryable     │  │ @CircuitBreaker│  │ logSync/STS  │   │
│  │ @Version (OL)  │  │ @Transactional │  │ + @Async     │   │
│  │                │  │ (REQUIRED: same │  │ (non-checkout│   │
│  │                │  │ TX as checkout)│  │  callers)    │   │
│  └────────────────┘  └───────┬────────┘  └──────────────┘   │
│                                                              │
│  OrderAnomalyTriageService (post-commit Kafka only;          │
│  ChatClient outside DB TX; flag default off)                 │
│                                                              │
└──────────────┬───────────────┼───────────────────────────────┘
               │               │
               ▼               ▼
┌──────────────────────┐  ┌──────────────────────────────────┐
│  PERSISTENCE LAYER   │  │  EXTERNAL SERVICES               │
│                      │  │                                    │
│  Spring Data JPA     │  │  Payment Gateway (mock | stripe)  │
│  12 repositories     │  │  ┌────────────────────────────┐   │
│  ┌────────────────┐  │  │  │ Resilience4j               │   │
│  │ CustomerRepo   │  │  │  │ • CircuitBreaker: 50%/10   │   │
│  │ ProductRepo    │  │  │  │ • Retry: 3 attempts        │   │
│  │ CategoryRepo   │  │  │  │ • RateLimiter: 100/sec     │   │
│  │ CartRepo       │  │  │  └────────────────────────────┘   │
│  │ OrderRepo      │  │  │                                    │
│  │ PaymentRepo    │  │  │  Spring AI ChatClient              │
│  │ AuditLogRepo   │  │  │  (stub in dev; OpenAI when         │
│  │ AddressRepo    │  │  │   triage flag + API key)           │
│  │ PwdResetTokRepo│  │  │                                    │
│  │ OutboxEventRepo│  │  └──────────────────────────────────┘
│  │ WebhookEventRepo│ │
│  │ AnomalyTriageRepo││
│  └────────────────┘  │
│                      │
│  Flyway migrations   │
│  V1–V8 (DDL)        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────────────────┐
│           DATABASE                        │
│                                           │
│  DEV:  H2 in-memory (dev profile)        │
│  DOCKER/K8S: PostgreSQL 15 (docker prof.)│
│                                           │
│  Tables: customers, addresses, products, │
│  categories, carts, cart_items, orders,  │
│  order_items, payments, audit_logs,      │
│  password_reset_tokens, outbox_events,   │
│  processed_webhook_events,               │
│  order_anomaly_triage                    │
└──────────────────────────────────────────┘

               ┌──────────────────────────────────────────┐
               │         EVENT-DRIVEN LAYER                │
               │                                           │
  OrderService │  OutboxService → outbox_events (same TX)  │
  ──enqueue──► │  OutboxPoller → OrderEventPublisher       │
               │  ──► Kafka Topic: "orders.created"       │
               │        │                                  │
               │        ├──────────────────────┐           │
               │        ▼                      ▼           │
               │  NotificationConsumer   OrderAnomalyTriageConsumer
               │  (notification-service) (order-anomaly-triage)
               │  ──► mock email log     ──► classify + persist
               │                         (skipped when flag off)
               │                                           │
               │  KafkaConfig:                             │
               │  • Producer: acks=all, idempotent         │
               │  • Consumer: 3 concurrent threads         │
               │  • JsonSerializer / JsonDeserializer      │
               └──────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    OBSERVABILITY                              │
│                                                              │
│  Spring Boot Actuator                                        │
│  ┌─────────────────────────┐  ┌───────────────────────────┐  │
│  │ /actuator/health        │  │ /actuator/inventory       │  │
│  │ PaymentGatewayHealth    │  │ InventoryEndpoint         │  │
│  │ Indicator               │  │ (custom: low stock report)│  │
│  └─────────────────────────┘  └───────────────────────────┘  │
│  /actuator/metrics  /actuator/info  /actuator/loggers        │
└──────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    DEPLOYMENT                                │
│                                                              │
│  Docker (multi-stage build; default target = JVM runtime)    │
│  ┌───────────┐ ┌──────────┐ ┌─────────┐ ┌───────────────┐   │
│  │ App :8080 │ │ PG :5432 │ │Kafka    │ │ Kafka UI      │   │
│  │ Temurin   │ │ (15-alp) │ │:9092    │ │ :8090         │   │
│  │ 25-jre    │ │          │ │Zookeeper│ └───────────────┘   │
│  │ ZGC +     │ │          │ │:2181    │                      │
│  │ preview   │ │          │ └─────────┘  MailHog :8025      │
│  └───────────┘ └──────────┘                                  │
│  Optional: docker build --target native-runtime              │
│  Kubernetes: k8s/local (Colima) + k8s/deployment.yaml        │
│  • Probes: /actuator/health/liveness + readiness             │
│  • ConfigMap + Secrets for env vars                          │
└──────────────────────────────────────────────────────────────┘
```

## Build and test

Use **JDK 25** (`mvn -v` must show 25.x). sdkman often defaults to 17 and overrides Homebrew — see [context/java25-disciplines.md](./context/java25-disciplines.md).

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-tem"   # or Homebrew Temurin 25
export PATH="$JAVA_HOME/bin:$PATH"
mvn -q verify
```

Checkout integration tests need Docker (Testcontainers Postgres). Without Docker they skip; `mvn verify` still passes.

## Running Locally

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) or [Colima](https://github.com/abiosoft/colima) (Apple Silicon supported)

### Start all services
```bash
docker-compose up -d
```

- App: http://localhost:8080
- Kafka UI: http://localhost:8090
- MailHog (password reset emails): http://localhost:8025
- Swagger UI: http://localhost:8080/swagger-ui.html

`docker-compose` activates the **`docker` profile** (PostgreSQL + Kafka + MailHog). The H2 console is available only when running with the **`dev` profile** (e.g. `mvn spring-boot:run` without Docker): http://localhost:8080/h2-console

Default payment provider is **mock** — gateway health may show DOWN; that is expected.

Optional AI triage: `ORDER_ANOMALY_TRIAGE_ENABLED=true` plus `SPRING_AI_OPENAI_API_KEY` in docker (app **fails fast** if the flag is on without a real key). Dev profile uses a stub model. See [DEPLOYMENT.md](./DEPLOYMENT.md).

### Maven on the host (`dev` profile)

H2 in-memory. Kafka expected on `localhost:9092` if you exercise outbox/consumers.

```bash
mvn spring-boot:run
```

### Docker notes
- Build stage uses `maven:3.9-eclipse-temurin-25` — no Maven wrapper needed
- Runtime stage uses `eclipse-temurin:25-jre` (Ubuntu) — ARM64 (Apple Silicon) and AMD64; `--enable-preview`, `-XX:+UseZGC`
- Non-root user (`appuser`) runs the process for security
- Optional native image: `docker build --target native-runtime -t ecommerce-api:native .` (GraalVM 25; Jersey native still unverified)

## API Endpoints

### Auth
POST /api/v1/auth/register       — Register + auto-login
POST /api/v1/auth/login          — Login, returns JWT
POST /api/v1/auth/forgot-password — Request password reset email (always 202)
POST /api/v1/auth/reset-password  — Set new password from reset token

### Products (public browse)
GET    /api/v1/products                  — Search with ?q=term&page=&size=
GET    /api/v1/products/{id}           — Get product detail
GET    /api/v1/products/category/{id}  — Products in category (paginated)
POST   /api/v1/products                  — Create (SELLER or ADMIN)
PUT    /api/v1/products/{id}             — Update (SELLER or ADMIN)
DELETE /api/v1/products/{id}             — Soft delete (SELLER or ADMIN)

### Categories
Public read access under `/api/v1/categories` (`SecurityConfig`). Create/update/delete require **SELLER** or **ADMIN** (`@PreAuthorize` on mutating routes).

GET    /api/v1/categories              — Top-level categories (paginated: `?page=&size=`)
GET    /api/v1/categories/{id}        — Category detail
GET    /api/v1/categories/{id}/subcategories — Child categories (paginated)
POST   /api/v1/categories             — Create (SELLER or ADMIN)
PUT    /api/v1/categories/{id}        — Update (SELLER or ADMIN)
DELETE /api/v1/categories/{id}        — Delete (SELLER or ADMIN)

### Addresses (authenticated)
Scoped to the logged-in customer (JWT → `UserDetails` → email → customer id).

GET    /api/v1/addresses       — List my addresses
POST   /api/v1/addresses       — Create address
PUT    /api/v1/addresses/{id}  — Update address
PATCH  /api/v1/addresses/{id}/default — Set default address
DELETE /api/v1/addresses/{id}  — Delete address

### Cart (authenticated)
GET    /api/v1/cart                    — View cart
POST   /api/v1/cart/items              — Add item
DELETE /api/v1/cart/items/{productId}  — Remove item by product id
DELETE /api/v1/cart                    — Clear cart

### Orders (authenticated)
POST  /api/v1/orders/checkout       — Checkout (idempotent)
GET   /api/v1/orders                — My orders (paginated)
GET   /api/v1/orders/{id}           — Order detail (own orders only)
PATCH /api/v1/orders/{id}/status    — Update status (ADMIN only)

### Admin (ADMIN role required)
GET /api/v1/admin/users             — List all users (paginated: ?page=0&size=20)
PUT /api/v1/admin/users/{id}/roles  — Replace a user's roles

**Role values:** `CUSTOMER`, `SELLER`, `ADMIN`

```bash
# Example: promote user 5 to SELLER
curl -X PUT 'http://localhost:8080/api/v1/admin/users/5/roles' \
  -H 'Authorization: Bearer <admin-token>' \
  -H 'Content-Type: application/json' \
  -d '{"roles": ["SELLER"]}'
```

**Guards:**
- An admin cannot remove their own `ADMIN` role
- The last admin in the system cannot be demoted
- All role changes are recorded in the audit log

### Webhooks (Stripe, when `PAYMENT_GATEWAY_PROVIDER=stripe`)
POST /api/v1/webhooks/stripe — Stripe signed webhook events (public)

### Jersey (JAX-RS equivalent)
GET    /jersey/products                  — Search (same logic as MVC)
GET    /jersey/products/{id}             — Product detail
GET    /jersey/products/category/{id}    — By category
POST   /jersey/products                  — Create (authenticated seller)
PUT    /jersey/products/{id}             — Update
DELETE /jersey/products/{id}             — Delete
POST   /jersey/orders/checkout           — Checkout
GET    /jersey/orders/{id}               — Order detail

### Observability
GET /actuator/health              — Full health (includes payment gateway indicator)
GET /actuator/health/liveness     — Liveness probe (JVM up)
GET /actuator/health/readiness    — Readiness probe (DB, etc.; gateway excluded)
GET /actuator/inventory           — Low stock report (admin)
GET /actuator/metrics             — Prometheus metrics

## Known gaps

### First admin

**Dev (`mvn spring-boot:run`):** Flyway seeds `admin@localhost` / `adminpass` (local-only; not applied in docker).

**Docker / prod:** set `ADMIN_EMAIL` and `ADMIN_PASSWORD`. `AdminBootstrap` creates that user when no `ADMIN` exists; if the vars are unset, the app still starts and logs a warning. Do not commit production passwords.

### Other limits
- Capstone load/native RSS numbers in ARCHITECTURE are still **not measured**.
- Native image: JVM remains the production default; Jersey + GraalVM reachability is unverified; AI starters are JVM-only.
- Failed-payment checkouts do **not** emit Kafka events (rollback); triage only sees successful `orders.created`.

---

## Key Design Decisions
- Idempotent checkout prevents duplicate orders (`idempotencyKey` on `POST /api/v1/orders/checkout`)
- `@Version` on `Product` plus retries on optimistic-lock failures reduces oversell under concurrency
- `PaymentService` uses default **`@Transactional` propagation (`REQUIRED`)** so payment rows are written in the **same transaction** as checkout; separate transactions risk FK violations against the not-yet-visible order row
- **Transactional outbox** — order-created Kafka events are written to `outbox_events` in the checkout transaction; `OutboxPoller` publishes asynchronously after commit
- JWT authentication stores a **`UserDetails` principal** in the security context (not only the email string) so authenticated controllers resolve the user reliably
- Kafka event payloads (`OrderCreatedEvent` and nested types) stay Jackson-friendly (constructors/setters as needed for deserialization)
- **AI order anomaly triage** (discipline 8) — optional post-commit consumer on `orders.created` (`ORDER_ANOMALY_TRIAGE_ENABLED`); Spring AI classify only, never on checkout TX
- Checkout audit runs **after commit** via `StructuredTaskScope` (`logSync` + `join`); other callers still use `@Async` `AuditService.log`
- Soft delete on products preserves order history integrity
- Payment provider is pluggable: **mock** (default) or **Stripe** via `app.payment-gateway.provider`; Stripe webhooks dedupe via `processed_webhook_events`
