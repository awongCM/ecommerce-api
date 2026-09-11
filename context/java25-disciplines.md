# Java 25 disciplines workstream

## 2026-09-11 — Discipline 8 shipped (order anomaly triage)

Implemented on `feature/ai-native-discipline-8` as **PR #4** (https://github.com/awongCM/ecommerce-api/pull/4), after PR #3 merged to `main`. Design spec: `docs/superpowers/specs/2026-09-11-ai-native-order-anomaly-triage-design.md`.

### What landed

- Sibling Kafka consumer `OrderAnomalyTriageConsumer` on `orders.created` (group `order-anomaly-triage`), **not** inside `OrderService.checkout` or post-commit STS.
- Spring AI **1.1.8** BOM + `spring-ai-starter-model-openai`; structured classify only (no RAG, no tools, no auto-refund).
- Labels: `LIKELY_FRAUD` / `GATEWAY_NOISE` / `CUSTOMER_RETRY` / `OPS_REVIEW` → Flyway **V8** `order_anomaly_triage`.
- Flag `app.features.order-anomaly-triage` / `ORDER_ANOMALY_TRIAGE_ENABLED` **default off**.
- Dev/tests: `@Primary` `StubChatModel` (`OPS_REVIEW`). Docker: OpenAI when key set; **fail-fast** at startup if flag on + stub (`OrderAnomalyTriageStartupValidator`).
- LLM call is **outside** DB transactions (read TX → classify → write TX). Concurrent insert on unique `order_id` is treated as success (`DataIntegrityViolationException`).
- External-provider prompts **redact** name, email local-part, card last4, and gateway ref (see DEPLOYMENT privacy note).

### Review findings that bit us

- Dual constructors on `OrderAnomalyTriageService` confused Spring (`No default constructor found`) — mark the production ctor `@Autowired` and keep the test factory private/`forTests`.
- Mockito strict stubbing + unused `executeWithoutResult` stubs on passthrough `TransactionTemplate` mocks — use `lenient()`.
- Do **not** hold a DB TX across `ChatClient.call()`.

### Still out of scope

Failed-payment Kafka/audit substrate; RAG; admin UI; notify on label; Jersey mirror; GraalVM AI reachability; LLM in checkout.

## 2026-09-11 — AI-native (discipline 8) follow-up decisions

Decided in chat after PR #3 work (implemented later the same day — see entry above).

### Repo shape

- **No separate GitHub repo required** for AI-native Java.
- Optional later: separate **process/module** for scaling/secrets — org choice, not a Comeback requirement.
- **Mandatory:** keep AI off the checkout/payment transaction. AI classifies/drafts; Java commits money and inventory.

### Preferred first slice (same `ecommerce-api` repo, new PR after #3 merges)

**Payment / order anomaly triage (post-commit):**

1. Trigger on Kafka `orders.created` and/or failed-payment audit (not inside `OrderService.checkout`).
2. Spring AI / LangChain4j classifies e.g. `likely_fraud` / `gateway_noise` / `customer_retry` / `ops_review`.
3. Persist structured result (+ optional notify); RAG over ops runbooks optional.
4. Ship behind a feature flag; own tests + ARCHITECTURE note.

**Also acceptable (same pattern):** support reply drafts, NL → catalog filters, circuit-open incident briefs.

**Skip as demos:** LLM in checkout/capture; auto-refund/cancel; chatbot with no domain data.

### Process

- Land **PR #3** first (`feature/java25-boot35-disciplines`).
- Open a **separate PR** for discipline 8 from updated `main` — do not bolt AI onto #3.

## 2026-09-11 — Branch status and hard-won operational notes

### Where the work lives

- **Branch:** `feature/java25-boot35-disciplines`
- **PR:** https://github.com/awongCM/ecommerce-api/pull/3
- **Worktree:** `.worktrees/java25-boot35` (do not develop this on `main`)
- **Plan:** `.worktrees/java25-boot35/docs/superpowers/plans/2026-09-04-java25-disciplines-completion.md`

### Disciplines closed in this PR

| # | Topic | How it landed |
|---|---|---|
| 2 + 6 | Patterns / modern Java | Sealed `PaymentOutcome`, exhaustive switch, `AuthResponse`/`ErrorResponse` records |
| 4 | JVM | Dockerfile `-XX:+UseZGC`, `scripts/capture-checkout-jfr.sh` (host JDK only) |
| 5 | Concurrency | Virtual threads enabled; `StructuredTaskScope` **after commit** for audit/notify; outbox stays in checkout TX |
| 7 | Native | `-Pnative` + Docker `native-runtime` stage; JVM image remains default |
| 9 | Capstone | `scripts/checkout-load.sh` + honest “not measured” table in ARCHITECTURE |
| 8 | AI | Post-commit `OrderAnomalyTriageConsumer` + Spring AI classify (separate PR from #3) |

Platform gate: Java **25** + Spring Boot **3.5.x**, `--enable-preview` for StructuredTaskScope.

### Local JDK gotchas (sdkman vs Homebrew)

- sdkman `current` often points at **Java 17**; it loads **after** Homebrew in `~/.zshrc`, so bare `mvn` on this branch fails with class-file 69.0 vs runtime max 61.0.
- For this branch use JDK 25 explicitly, e.g.:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-tem"   # or graalce for native
export PATH="$JAVA_HOME/bin:$PATH"
mvn -v   # must show 25.x
mvn -q clean verify
```

- Surefire needs Mockito as a javaagent on JDK 25 (`-javaagent:${org.mockito:mockito-core:jar}` + `-XX:+EnableDynamicAgentLoading` via `maven-dependency-plugin` properties). Without it: “Could not self-attach”.

### PaymentOutcome contract (do not regress)

- Non-retryable `PaymentGatewayException` → `PaymentOutcome.Failed`
- Retryable `PaymentGatewayException` → **rethrow** so Resilience4j `@Retry` / `@CircuitBreaker` can open and `paymentFallback` returns `GatewayUnavailable`
- Declines must not be treated as gateway unavailable

### Native image (discipline 7)

- Needs **GraalVM** (`25.0.2-graalce`), not Temurin — Temurin has no `native-image`.
- Do **not** pass `--initialize-at-build-time=org.slf4j`; it puts `LogbackMDCAdapter` in the image heap while Logback stays run-time init → `UnsupportedFeatureException`.
- After that fix, `mvn -Pnative -DskipTests native:compile` succeeded (~227MB `target/ecommerce-api` on arm64).
- Jersey native reachability still treated as unverified; JVM remains production default.
- Docker: `docker build --target native-runtime -t ecommerce-api:native .`

### Capstone / load script

- `TOKEN=<jwt> SHIPPING_ADDRESS_ID=<id> ./scripts/checkout-load.sh`
- Placeholders like `you@example.com` fail login (401, no `token` key) — register or use a real account.
- Concurrent requests share one cart; after first success others often fail empty-cart — fine as a harness, weak as “20 concurrent checkout” evidence.
- Capstone metrics table in ARCHITECTURE still `not measured` / `n/a` until someone runs load + native RSS capture.

### Review fixes already on the branch

- Flyway 11 needs `flyway-database-postgresql` for docker/Postgres
- Docker ENTRYPOINT must include `--enable-preview`
- After-commit STS + `AuditContext` snapshot (MDC/SecurityContext do not auto-propagate)
- CI workflow: `.github/workflows/ci.yml` (Temurin 25 `mvn verify`)

### Why discipline 8 was deferred from PR #3

Blog intent: Spring AI / LangChain4j on the JVM (RAG, agents), not “call an LLM from a controller once.”

Deferred from the platform PR because:
1. **Scope** — this PR’s job was the platform restart (25 / Boot 3.5) + prove 2/4/5/6/7/9 on checkout; AI is a second product slice.
2. **Hot path** — checkout/payment must stay deterministic, latency-bounded, and cheap; an LLM on that path fights ARCHITECTURE invariants.
3. **Missing substrate** — no vector store, embedding pipeline, document corpus, or Spring AI/LangChain4j deps; no secrets/runbook for model providers.
4. **Native already fragile** — Jersey + GraalVM still unverified; adding AI SDKs would explode reachability work before the JVM default is solid.
5. **Honest craft** — a thin “classify this string” wrapper would tick a table cell without proving AI-on-JVM skill.

Sensible later shape (now implemented in PR #4): **offline / post-commit** path (payment/order anomaly triage over Kafka `orders.created`), never inside the checkout TX.
