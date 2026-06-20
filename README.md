# ecommerce-api

A production-grade RESTful ecommerce API built with Spring Boot 3.x.

## Tech Stack
- Java 17, Spring Boot 3.2
- Spring Security + JWT authentication
- Spring Data JPA + PostgreSQL + Flyway
- Apache Kafka (event-driven notifications)
- Resilience4j (circuit breaker, retry, rate limiter)
- Jersey (JAX-RS) — parallel implementation for comparison
- Docker + Kubernetes-ready

## Architecture

Design heuristics (layering, transactions, security, testing) live in [ARCHITECTURE.md](./ARCHITECTURE.md).

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
│  │   → outbox enqueue → async audit)                     │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐   │
│  │InventoryService│  │ PaymentService │  │ AuditService │   │
│  │ @Retryable     │  │ @CircuitBreaker│  │ @Async       │   │
│  │ @Version (OL)  │  │ @Transactional │  │              │   │
│  │                │  │ (REQUIRED: same │  │              │   │
│  │                │  │ TX as checkout)│  │              │   │
│  └────────────────┘  └───────┬────────┘  └──────────────┘   │
│                              │                               │
└──────────────┬───────────────┼───────────────────────────────┘
               │               │
               ▼               ▼
┌──────────────────────┐  ┌──────────────────────────────────┐
│  PERSISTENCE LAYER   │  │  EXTERNAL SERVICES               │
│                      │  │                                    │
│  Spring Data JPA     │  │  Payment Gateway (mock | stripe)  │
│  11 repositories     │  │  ┌────────────────────────────┐   │
│  ┌────────────────┐  │  │  │ Resilience4j               │   │
│  │ CustomerRepo   │  │  │  │ • CircuitBreaker: 50%/10   │   │
│  │ ProductRepo    │  │  │  │ • Retry: 3 attempts        │   │
│  │ CategoryRepo   │  │  │  │ • RateLimiter: 100/sec     │   │
│  │ CartRepo       │  │  │  └────────────────────────────┘   │
│  │ OrderRepo      │  │  │                                    │
│  │ PaymentRepo    │  │  └──────────────────────────────────┘
│  │ AuditLogRepo   │  │
│  │ AddressRepo    │  │
│  │ PwdResetTokRepo│  │
│  │ OutboxEventRepo│  │
│  │ WebhookEventRepo│ │
│  └────────────────┘  │
│                      │
│  Flyway migrations   │
│  V1–V7 (DDL)        │
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
│  processed_webhook_events                │
└──────────────────────────────────────────┘

               ┌──────────────────────────────────────────┐
               │         EVENT-DRIVEN LAYER                │
               │                                           │
  OrderService │  OutboxService → outbox_events (same TX)  │
  ──enqueue──► │  OutboxPoller → OrderEventPublisher       │
               │  ──► Kafka Topic: "orders.created"       │
               │        │                                  │
               │        ▼                                  │
               │  NotificationConsumer                     │
               │  (groupId: notification-service)          │
               │  ──► Order email (log mock; reset uses SMTP)│
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
│  Docker (multi-stage build)                                  │
│  ┌───────────┐ ┌──────────┐ ┌─────────┐ ┌───────────────┐   │
│  │ App :8080 │ │ PG :5432 │ │Kafka    │ │ Kafka UI      │   │
│  │ (JRE 17)  │ │ (15-alp) │ │:9092    │ │ :8090         │   │
│  └───────────┘ └──────────┘ │Zookeeper│ └───────────────┘   │
│                              │:2181    │                      │
│  Kubernetes                  └─────────┘                     │
│  • 3 replicas, LoadBalancer                                  │
│  • Readiness + Liveness probes via /actuator/health          │
│  • ConfigMap + Secrets for env vars                          │
└──────────────────────────────────────────────────────────────┘
```

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

### Docker notes
- Build stage uses `maven:3.9-eclipse-temurin-17` — no Maven wrapper needed
- Runtime stage uses `eclipse-temurin:17-jre` (Ubuntu) — works on ARM64 (Apple Silicon) and AMD64
- Non-root user (`appuser`) runs the process for security

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

## Known Gaps / TODO

### Admin Bootstrapping (needs implementation)
The admin role-management endpoint requires `ROLE_ADMIN` to call, but there is currently
no way to create the first admin through the API (chicken-and-egg problem).

**Options to implement:**

- [ ] **Option A — `DataInitializer` on startup (recommended for prod)**
  A `CommandLineRunner` bean that checks `countByRole(ADMIN) == 0` on startup and
  creates a default admin from environment variables (`ADMIN_EMAIL`, `ADMIN_PASSWORD`).
  No hardcoded credentials in source code.

- [ ] **Option B — Flyway seed migration (simple, good for dev)**
  Add a new migration (for example `V8__seed_admin.sql`) that inserts a bcrypt-hashed admin account. Suitable for
  local development; avoid hardcoding real credentials for production. (`V6`/`V7` are already used for outbox and webhook idempotency.)

- [ ] **Option C — Both** — Flyway migration for dev profile, `DataInitializer` for prod.

Until this is resolved, insert an admin manually in the database.

**Docker Compose (PostgreSQL):**
```bash
docker exec -it ecommerce-postgres psql -U ecommerceuser -d ecommercedb
```
```sql
INSERT INTO customers (first_name, last_name, email, password_hash)
VALUES ('Admin', 'User', 'admin@example.com', '<bcrypt-hash>');

INSERT INTO customer_roles (customer_id, role)
SELECT id, 'ADMIN' FROM customers WHERE email = 'admin@example.com';
```

**Dev profile (H2):** use the H2 console at http://localhost:8080/h2-console with the same SQL (JDBC URL from `application-dev.yml`).

---

## Key Design Decisions
- Idempotent checkout prevents duplicate orders (`idempotencyKey` on `POST /api/v1/orders/checkout`)
- `@Version` on `Product` plus retries on optimistic-lock failures reduces oversell under concurrency
- `PaymentService` uses default **`@Transactional` propagation (`REQUIRED`)** so payment rows are written in the **same transaction** as checkout; separate transactions risk FK violations against the not-yet-visible order row
- **Transactional outbox** — order-created Kafka events are written to `outbox_events` in the checkout transaction; `OutboxPoller` publishes asynchronously after commit
- JWT authentication stores a **`UserDetails` principal** in the security context (not only the email string) so authenticated controllers resolve the user reliably
- Kafka event payloads (`OrderCreatedEvent` and nested types) stay Jackson-friendly (constructors/setters as needed for deserialization)
- `@Async` on `AuditService` keeps audit persistence off the critical request path
- Soft delete on products preserves order history integrity
- Payment provider is pluggable: **mock** (default) or **Stripe** via `app.payment-gateway.provider`; Stripe webhooks dedupe via `processed_webhook_events`

