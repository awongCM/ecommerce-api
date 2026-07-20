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

echo "Importing image into k3s (Colima VM)..."
docker save "${IMAGE}" | colima ssh -- sudo k3s ctr -n k8s.io images import -

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
