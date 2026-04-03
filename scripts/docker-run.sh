#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PORT="${PORT:-8080}"
DEMO_TOKEN="${DEMO_TOKEN:-eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1MSIsImlhdCI6MTc3NDkxMTYwMCwiZXhwIjo0MTAyNDQ0ODAwfQ.CamontEZemyd1HDeWeh9HoMgkEzaHMO4WP0GIYwgBXE}"

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

echo "Starting PostgreSQL and REST API on http://localhost:$PORT"
echo "Health check: curl http://localhost:$PORT/health"
echo "Demo JWT for user u1:"
echo "$DEMO_TOKEN"
echo "Permission check example: curl -H \"Authorization: Bearer \$DEMO_TOKEN\" http://localhost:$PORT/v1/documents/d1/permissions/can_view"
echo "Create user example: curl -X POST -H \"Content-Type: application/json\" --data @examples/write/create-user-u3.json http://localhost:$PORT/v1/users"
echo "Create document example: curl -X POST -H \"Content-Type: application/json\" --data @examples/write/create-document-d7.json http://localhost:$PORT/v1/documents"

cd "$ROOT_DIR"
docker compose up --build
