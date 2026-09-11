# AI-native order anomaly triage (discipline 8)

**Date:** 2026-09-11  
**Status:** Approved for implementation  
**Branch:** separate PR from `main` (after PR #3 merge)

## Purpose

Add a post-commit, AI-assisted order anomaly triage path on the JVM using Spring AI. The LLM classifies successful checkout events for ops review; it never participates in checkout, payment capture, or inventory commits.

## Decisions

| Topic | Choice |
|-------|--------|
| Trigger | Kafka `orders.created` only (successful checkouts) |
| Stack | Spring AI **1.1.8** (Boot 3.5 line) |
| Scope | Structured classify only — no RAG, tools, or auto-refund |
| Models | Stub `ChatModel` for dev/tests; OpenAI when flag + API key in docker/prod |
| Topology | Sibling `@KafkaListener` (`order-anomaly-triage` group) |
| Feature flag | `app.features.order-anomaly-triage=false` by default |

## Architecture

```
OrderService.checkout (TX) → outbox_events → OutboxPoller → orders.created
                                                                    ├→ NotificationConsumer
                                                                    └→ OrderAnomalyTriageConsumer → ChatClient → order_anomaly_triage
```

Hard rule: AI classifies/drafts; Java commits money and inventory.

## Components

- **OrderAnomalyTriageConsumer** — listens on `orders.created`; skips when flag off
- **OrderAnomalyTriageService** — idempotent classify + persist; enriches from payment row
- **AnomalyClassification** — `LIKELY_FRAUD`, `GATEWAY_NOISE`, `CUSTOMER_RETRY`, `OPS_REVIEW`
- **order_anomaly_triage** (Flyway V8) — structured result storage
- **AiConfig** — stub `@Primary` in dev; OpenAI auto-config when key present
- **StubChatModel** — deterministic `OPS_REVIEW` JSON for dev/CI

## Runtime behavior

- **Flag off:** consumer returns; offset committed; no AI call
- **Already triaged:** unique `order_id` → no-op (at-least-once safe)
- **Missing order/payment:** log warn; commit offset (no infinite retry)
- **LLM failure:** rethrow for Kafka retry
- **Native image:** AI stack JVM-only; documented out of scope for GraalVM

## Testing

- Unit tests for service (persist, idempotency, invalid output)
- Integration test with Postgres + stub model; Kafka listeners `auto-startup=false`
- `mvn verify` on Java 25 without live API key

## Out of scope

Failed-payment Kafka/audit substrate; RAG; admin UI; notify on label; Jersey mirror; GraalVM AI reachability; LLM in checkout.
