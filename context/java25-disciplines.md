# Java 25 disciplines workstream

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
| 8 | AI | Intentionally skipped |

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

### Why discipline 8 (AI-native Java) was skipped

Blog intent: Spring AI / LangChain4j on the JVM (RAG, agents), not “call an LLM from a controller once.”

Deferred because:
1. **Scope** — this PR’s job was the platform restart (25 / Boot 3.5) + prove 2/4/5/6/7/9 on checkout; AI is a second product slice.
2. **Hot path** — checkout/payment must stay deterministic, latency-bounded, and cheap; an LLM on that path fights ARCHITECTURE invariants.
3. **Missing substrate** — no vector store, embedding pipeline, document corpus, or Spring AI/LangChain4j deps; no secrets/runbook for model providers.
4. **Native already fragile** — Jersey + GraalVM still unverified; adding AI SDKs would explode reachability work before the JVM default is solid.
5. **Honest craft** — a thin “classify this string” wrapper would tick a table cell without proving AI-on-JVM skill.

Sensible later shape: **offline / post-commit** path (e.g. payment-anomaly triage over outbox/Kafka events), never inside the checkout TX.
