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
│  └────────────────────┘  │       │  │ JerseyExceptionMap │  │
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
# Start all services
docker compose up

# App runs at http://localhost:8080
# Kafka UI at http://localhost:8090
# H2 Console (dev) at http://localhost:8080/h2-console

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
