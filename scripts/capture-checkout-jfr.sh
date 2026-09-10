#!/usr/bin/env bash
# Captures a JFR recording of one checkout cycle against a running local app.
# Usage: ./scripts/capture-checkout-jfr.sh
# Requires: jcmd on PATH, app running on localhost:8080 with docker-compose.

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
