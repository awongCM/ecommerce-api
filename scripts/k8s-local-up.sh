#!/usr/bin/env bash
# Local Kubernetes stack for ecommerce-api (Colima + k3s).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${IMAGE:-ecommerce-api:local}"
NAMESPACE="${NAMESPACE:-ecommerce-local}"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

need docker
need kubectl
need colima

colima_runtime() {
  colima status 2>&1 | sed -n 's/.*runtime: \([^"]*\)".*/\1/p' | head -1
}

import_image() {
  local runtime
  runtime="$(colima_runtime)"

  if [[ "${runtime}" == "docker" ]]; then
    # Colima docker+k3s shares the Docker daemon — no ctr import needed.
    echo "Colima runtime is docker; k3s uses local Docker images directly."
    return 0
  fi

  echo "Importing image into k3s containerd (runtime: ${runtime:-unknown})..."
  if docker save "${IMAGE}" | colima ssh -- sudo ctr -n k8s.io images import -; then
    return 0
  fi

  echo "ctr import failed. If you use containerd runtime, try:" >&2
  echo "  colima nerdctl --namespace k8s.io build -t ${IMAGE} ${ROOT}" >&2
  exit 1
}

if ! colima status >/dev/null 2>&1; then
  echo "Starting Colima with Kubernetes..."
  colima start --kubernetes --cpu 4 --memory 6
elif ! kubectl get nodes >/dev/null 2>&1; then
  echo "Colima is running but Kubernetes is not reachable."
  echo "Restart with: colima stop && colima start --kubernetes --cpu 4 --memory 6"
  exit 1
fi

echo "Building image ${IMAGE}..."
docker build -t "${IMAGE}" "${ROOT}"

import_image

echo "Applying manifests..."
kubectl apply -k "${ROOT}/k8s/local"

echo "Waiting for deployments..."
kubectl -n "${NAMESPACE}" rollout status deployment/postgres --timeout=120s
kubectl -n "${NAMESPACE}" rollout status deployment/kafka --timeout=180s
kubectl -n "${NAMESPACE}" rollout status deployment/ecommerce-api --timeout=300s

echo
echo "Ready. Forward the API to localhost:"
echo "  kubectl -n ${NAMESPACE} port-forward svc/ecommerce-api 8080:8080"
echo
echo "Then open:"
echo "  http://localhost:8080/actuator/health/readiness"
echo "  http://localhost:8080/swagger-ui.html"
