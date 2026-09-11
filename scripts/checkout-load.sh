#!/usr/bin/env bash
# Concurrent checkout load — proves virtual threads under real checkout load.
# Usage: BASE_URL=http://localhost:8080 TOKEN=<jwt> ./scripts/checkout-load.sh
#
# Prerequisites:
#   - App running (docker-compose up -d or mvn spring-boot:run)
#   - curl on PATH
#   - A customer account with at least one address and one cart item
#
# Output: concurrency count, success count, failure count, total time.
#
# --- Maintaining / updating this script by hand ---
# Edit this file directly; there is no generator. Prefer env vars over new flags unless UX needs it.
#
# Knobs (env, with defaults above):
#   BASE_URL, TOKEN (required), SHIPPING_ADDRESS_ID, CONCURRENCY, PAYLOAD_TEMPLATE
# Keep PAYLOAD_TEMPLATE aligned with CheckoutRequest JSON (shippingAddressId, idempotencyKey,
# paymentToken). Unique idempotencyKey per request is required — do not share one key across the loop.
#
# Invariants:
#   - Write each HTTP status to its own temp file (no shared counter under &); then tally after wait.
#   - Treat 200 and 201 as success; print non-success codes to stderr.
#   - One cart is shared across concurrent requests: after the first success, later calls often fail
#     empty-cart — expected for this harness; change seed data (or per-user carts) if you need N
#     true successes for capstone evidence in ARCHITECTURE.md.
#
# After editing: chmod +x if needed; register/login for TOKEN; ensure cart + address; run with
# CONCURRENCY=2 first, then raise. Update ARCHITECTURE.md "Capstone evidence" if metrics/script
# contract changes. Capstone context: context/java25-disciplines.md (discipline 9).

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:?Set TOKEN to a valid JWT}"
SHIPPING_ADDRESS_ID="${SHIPPING_ADDRESS_ID:-1}"
CONCURRENCY="${CONCURRENCY:-20}"
PAYLOAD_TEMPLATE='{"shippingAddressId":%s,"idempotencyKey":"%s","paymentToken":"tok_visa"}'

now_ms() {
    python3 -c 'import time; print(int(time.time() * 1000))'
}

generate_uuid() {
    if command -v uuidgen >/dev/null 2>&1; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    elif [[ -r /proc/sys/kernel/random/uuid ]]; then
        cat /proc/sys/kernel/random/uuid
    else
        # Fallback: random hex (unique enough for idempotency keys in load tests)
        od -An -N16 -tx /dev/urandom | tr -d ' \n'
    fi
}

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

success=0
fail=0
declare -a pids

echo "Firing $CONCURRENCY concurrent checkout requests..."
start=$(now_ms)

for i in $(seq 1 "$CONCURRENCY"); do
    key=$(generate_uuid)
    payload=$(printf "$PAYLOAD_TEMPLATE" "$SHIPPING_ADDRESS_ID" "$key")
    result_file="$tmpdir/result-$i"
    (
        if ! http_code=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST "$BASE_URL/api/v1/orders/checkout" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/json" \
            -d "$payload"); then
            http_code="000"
        fi
        printf '%s' "$http_code" >"$result_file"
    ) &
    pids+=($!)
done

for pid in "${pids[@]}"; do
    wait "$pid" || true
done

for i in $(seq 1 "$CONCURRENCY"); do
    code=$(cat "$tmpdir/result-$i")
    if [[ "$code" == "200" || "$code" == "201" ]]; then
        ((success++)) || true
    else
        ((fail++)) || true
        echo "  request $i: HTTP $code" >&2
    fi
done

end=$(now_ms)
elapsed_ms=$(( end - start ))

echo ""
echo "Results:"
echo "  Concurrency : $CONCURRENCY"
echo "  Success     : $success"
echo "  Failed      : $fail"
echo "  Total time  : ${elapsed_ms}ms"
