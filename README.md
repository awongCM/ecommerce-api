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
│  │ CartController     │  │       │  │                    │  │
│  │ OrderController    │  │       │  │ JerseyAuthFilter   │  │
│  │ AdminController    │  │       │  │ JerseyExceptionMap │  │
│  └────────────────────┘  │       │  └────────────────────┘  │
│                          │       │  └────────────────────┘  │
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
│  │ @Version (OL)  │  │ REQUIRES_NEW   │  │              │   │
│  └────────────────┘  └───────┬────────┘  └──────────────┘   │
│                              │                               │
└──────────────┬───────────────┼───────────────────────────────┘
               │               │
               ▼               ▼
┌──────────────────────┐  ┌──────────────────────────────────┐
│  PERSISTENCE LAYER   │  │  EXTERNAL SERVICES               │
│                      │  │                                    │
│  Spring Data JPA     │  │  Payment Gateway (mock)           │
│  6 Repositories:     │  │  ┌────────────────────────────┐   │
│  ┌────────────────┐  │  │  │ Resilience4j               │   │
│  │ CustomerRepo   │  │  │  │ • CircuitBreaker: 50%/10   │   │
│  │ ProductRepo    │  │  │  │ • Retry: 3 attempts        │   │
│  │ CartRepo       │  │  │  │ • RateLimiter: 100/sec     │   │
│  │ OrderRepo      │  │  │  └────────────────────────────┘   │
│  │ PaymentRepo    │  │  │                                    │
│  │ AuditLogRepo   │  │  └──────────────────────────────────┘
│  └────────────────┘  │
│                      │
│  Flyway Migrations   │
│  V1-V4 (DDL)        │
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
│  order_items, payments, audit_logs       │
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
GET /actuator/health          — Health check
GET /actuator/inventory       — Low stock report (admin)
GET /actuator/metrics         — Prometheus metrics

## Key Design Decisions
- Idempotent checkout prevents duplicate orders
- @Version on Product prevents inventory oversell
- REQUIRES_NEW on PaymentService isolates payment DB record
- @Async on AuditService never slows the main request
- Soft delete on Products preserves order history integrity
