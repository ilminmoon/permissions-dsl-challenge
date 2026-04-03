#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
IMAGE_NAME="${IMAGE_NAME:-authz-policy-engine:local}"
PORT="${PORT:-8080}"

PLATFORM="$(uname -s)"

case "$PLATFORM" in
  Linux|Darwin)
    ;;
  MINGW*|MSYS*|CYGWIN*)
    echo "Windows shell detected. Use scripts\\docker-run.ps1 instead."
    exit 1
    ;;
  *)
    echo "Unsupported platform: $PLATFORM"
    exit 1
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required but was not found in PATH."
  exit 1
fi

echo "Building Docker image: $IMAGE_NAME"
docker build -t "$IMAGE_NAME" "$ROOT_DIR"

echo "Starting REST API on http://localhost:$PORT"
echo "Health check: curl http://localhost:$PORT/health"
echo "Permission check example: curl -X POST http://localhost:$PORT/v1/permission-checks -H 'Content-Type: application/json' --data @examples/rest/scenario-1-can-view.json"

docker run --rm -p "$PORT:8080" "$IMAGE_NAME"
