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
│  │   → Kafka event → async audit)                        │   │
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
│  Spring Data JPA     │  │  Payment Gateway (mock)           │
│  9 repositories      │  │  ┌────────────────────────────┐   │
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
│  └────────────────┘  │
│                      │
│  Flyway migrations   │
│  V1–V5 (DDL)        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────────────────┐
│           DATABASE                        │
│                                           │
│  DEV:  H2 In-Memory (application-dev)    │
│  PROD: PostgreSQL 15 (application-prod)  │
│                                           │
│  Tables: customers, addresses, products, │
│  categories, carts, cart_items, orders,  │
│  order_items, payments, audit_logs,      │
│  password_reset_tokens                   │
└──────────────────────────────────────────┘

               ┌──────────────────────────────────────────┐
               │         EVENT-DRIVEN LAYER                │
               │                                           │
  OrderService │  OrderEventPublisher                      │
  ──publish──► │  ──► Kafka Topic: "orders.created"       │
               │        │                                  │
               │        ▼                                  │
               │  NotificationConsumer                     │
               │  (groupId: notification-service)          │
               │  ──► Email notification (mock)            │
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
- H2 Console (dev): http://localhost:8080/h2-console
- Swagger UI: http://localhost:8080/swagger-ui.html

### Docker notes
- Build stage uses `maven:3.9-eclipse-temurin-17` — no Maven wrapper needed
- Runtime stage uses `eclipse-temurin:17-jre` (Ubuntu) — works on ARM64 (Apple Silicon) and AMD64
- Non-root user (`appuser`) runs the process for security

## API Endpoints

### Auth
POST /api/v1/auth/register  — Register + auto-login
POST /api/v1/auth/login     — Login, returns JWT

### Products (public browse)
GET  /api/v1/products        — Search with ?q=term
GET  /api/v1/products/{id}   — Get product detail
POST /api/v1/products        — Create (SELLER only)

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
GET    /api/v1/cart           — View cart
POST   /api/v1/cart/items     — Add item
DELETE /api/v1/cart/items/{id} — Remove item

### Orders (authenticated)
POST /api/v1/orders/checkout  — Checkout (idempotent)
GET  /api/v1/orders           — My orders

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

### Jersey (JAX-RS equivalent)
GET  /jersey/products         — Same logic, JAX-RS annotations
POST /jersey/orders/checkout  — Same logic, Jersey filter auth

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
  Add a new migration (for example `V6__seed_admin.sql`) that inserts a bcrypt-hashed admin account. Suitable for
  local development; avoid hardcoding real credentials for production. (`V5` is already used for `password_reset_tokens`.)

- [ ] **Option C — Both** — Flyway migration for dev profile, `DataInitializer` for prod.

Until this is resolved, the workaround is to manually insert via the H2 console
(`http://localhost:8080/h2-console`) or directly in the database:
```sql
INSERT INTO customers (first_name, last_name, email, password_hash)
VALUES ('Admin', 'User', 'admin@example.com', '<bcrypt-hash>');

INSERT INTO customer_roles (customer_id, role)
SELECT id, 'ADMIN' FROM customers WHERE email = 'admin@example.com';
```

---

## Key Design Decisions
- Idempotent checkout prevents duplicate orders (`idempotencyKey` on `POST /api/v1/orders/checkout`)
- `@Version` on `Product` plus retries on optimistic-lock failures reduces oversell under concurrency
- `PaymentService` uses default **`@Transactional` propagation (`REQUIRED`)** so payment rows are written in the **same transaction** as checkout; separate transactions risk FK violations against the not-yet-visible order row
- JWT authentication stores a **`UserDetails` principal** in the security context (not only the email string) so authenticated controllers resolve the user reliably
- Kafka event payloads (`OrderCreatedEvent` and nested types) stay Jackson-friendly (constructors/setters as needed for deserialization)
- `@Async` on `AuditService` keeps audit persistence off the critical request path
- Soft delete on products preserves order history integrity

