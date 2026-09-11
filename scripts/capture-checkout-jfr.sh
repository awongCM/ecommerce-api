#!/usr/bin/env bash
# Captures a JFR recording of one checkout cycle against a running local app.
# Usage: ./scripts/capture-checkout-jfr.sh  (run from repo root)
# Requires: jcmd/jps on PATH (JDK tools — not in eclipse-temurin:25-jre).
# Targets a host-run JDK process (mvn spring-boot:run / local JDK), not the Docker JRE image.
# filename=target/... is relative to the JVM process cwd — run from repo root so output lands in target/.
#
# --- Maintaining / updating this script by hand ---
# Edit this file directly; there is no generator. Keep it POSIX-friendly bash + JDK CLI tools.
#
# Knobs you may change:
#   - Process match (jps -l | grep …): must still uniquely identify the ecommerce-api JVM.
#   - RECORDING_FILE path/prefix: keep under target/ (gitignored); cwd of the JVM matters.
#   - JFR.start duration= / settings= / name=: duration must finish before you stop the app;
#     settings=profile is fine for pinning/GC; use default for lighter files.
#
# Do not:
#   - Point this at the Docker JRE image (no jcmd/jps there) without documenting a new host flow.
#   - Hard-code a PID or assume Docker PID namespaces.
#
# After editing: chmod +x if needed; start app with JDK 25; run script from repo root; confirm a
# .jfr appears and opens in jmc. Update ARCHITECTURE.md "JFR profiling" if behavior/docs drift.
# Capstone context: context/java25-disciplines.md (discipline 4).

set -euo pipefail

APP_PID=$(jps -l | grep ecommerce-api | awk '{print $1}')
if [[ -z "$APP_PID" ]]; then
  echo "ERROR: ecommerce-api not found in jps output. Is it running?" >&2
  exit 1
fi

RECORDING_FILE="target/checkout-$(date +%Y%m%d-%H%M%S).jfr"
echo "Starting JFR on PID $APP_PID → $RECORDING_FILE"

jcmd "$APP_PID" JFR.start name=checkout duration=60s filename="$RECORDING_FILE" \
  settings=profile

echo "Recording started for 60s. Run a checkout via:"
echo "  POST http://localhost:8080/api/v1/orders/checkout"
echo ""
echo "Recording will auto-stop after 60s."
echo "Open in JDK Mission Control: jmc $RECORDING_FILE"
