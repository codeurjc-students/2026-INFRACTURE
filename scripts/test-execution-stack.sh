#!/usr/bin/env bash
# Starts the containerized platform for the execution browser test and keeps it up until
# Playwright stops it. It runs as its own Compose project with its own database and
# instance id, so the developer's data and experiments are never touched.

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIRECTORY}/.." && pwd)"
PROJECT="infracture-e2e"
BACKEND_URL="http://127.0.0.1:8080"

log() {
  printf '[execution-test] %s\n' "$1"
}

fail() {
  printf '[execution-test] Error: %s\n' "$1" >&2
  exit 1
}

# Disposable values: the database is removed with the project at the end.
export INFRACTURE_INSTANCE_ID="e2e"
export INFRACTURE_ADMIN_USERNAME="e2e_admin"
export INFRACTURE_ADMIN_EMAIL="e2e.admin@example.test"
export INFRACTURE_ADMIN_DISPLAY_NAME="Test admin"
export INFRACTURE_ADMIN_PASSWORD="A disposable e2e admin password 2026!"

COMPOSE=(docker compose -p "${PROJECT}" --project-directory "${REPOSITORY_ROOT}"
  -f "${REPOSITORY_ROOT}/compose.yaml" --profile app)

cleanup() {
  trap - EXIT INT TERM
  set +e
  log "Removing the ${PROJECT} project, its database and any experiment left by label"
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1
  for kind in container network volume; do
    ids="$(docker "${kind}" ls -q $([[ "${kind}" == container ]] && echo --all) \
      --filter "label=io.infracture.instance=${INFRACTURE_INSTANCE_ID}")"
    [[ -n "${ids}" ]] && docker "${kind}" rm -f ${ids} >/dev/null 2>&1
  done
}

trap cleanup EXIT
trap 'exit 130' INT TERM

command -v docker >/dev/null 2>&1 || fail "Docker is required."
docker info >/dev/null 2>&1 || fail "Docker is not running."
if curl --silent --output /dev/null --connect-timeout 1 "${BACKEND_URL}"; then
  fail "Port 8080 is already serving another process. Stop the development backend first."
fi

log "Building the demo image used by the experiment"
docker build --quiet -t infracture/demo-app:0.1.0 "${REPOSITORY_ROOT}/demo-app" >/dev/null

log "Starting PostgreSQL, docker-proxy and the backend in containers"
"${COMPOSE[@]}" up -d --wait --build

log "Platform ready at ${BACKEND_URL}"
# Playwright sends SIGTERM when the tests end; the trap then removes everything.
while true; do
  sleep 1
done
