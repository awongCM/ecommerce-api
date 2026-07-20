#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${NAMESPACE:-ecommerce-local}"

kubectl delete -k "${ROOT}/k8s/local" --ignore-not-found
echo "Removed namespace ${NAMESPACE} (if it existed)."
