#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIRECTORY}/.." && pwd)"
BACKEND_DIRECTORY="${REPOSITORY_ROOT}/backend"
FRONTEND_DIRECTORY="${REPOSITORY_ROOT}/frontend"
BACKEND_URL="http://127.0.0.1:8081"
TEMPORARY_DIRECTORY="$(mktemp -d)"
BACKEND_LOG="${TEMPORARY_DIRECTORY}/backend.log"
BACKEND_PID=""

log() {
  printf '[integration-test] %s\n' "$1"
}

fail() {
  printf '[integration-test] Error: %s\n' "$1" >&2
  exit 1
}

show_backend_log() {
  printf '[integration-test] Backend log:\n' >&2
  tail -n 240 "${BACKEND_LOG}" >&2
}

cleanup() {
  local exit_code=$?

  trap - EXIT INT TERM
  set +e

  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    log "Stopping Spring Boot and its disposable PostgreSQL container"
    kill "${BACKEND_PID}" >/dev/null 2>&1
    wait "${BACKEND_PID}" >/dev/null 2>&1
  fi

  rm -f "${BACKEND_LOG}"
  rmdir "${TEMPORARY_DIRECTORY}" 2>/dev/null

  exit "${exit_code}"
}

handle_signal() {
  exit 130
}

wait_for_backend() {
  local attempts=0
  local maximum_attempts=120

  log "Waiting for Spring Boot and Flyway at ${BACKEND_URL}"

  until curl --fail --silent --show-error \
    "${BACKEND_URL}/actuator/health" >/dev/null 2>&1; do
    if ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
      wait "${BACKEND_PID}" || true
      show_backend_log
      fail "Backend startup failed before becoming healthy."
    fi

    attempts=$((attempts + 1))
    if ((attempts >= maximum_attempts)); then
      show_backend_log
      fail "Backend startup exceeded ${maximum_attempts} seconds."
    fi

    sleep 1
  done
}

trap cleanup EXIT
trap handle_signal INT TERM

command -v docker >/dev/null 2>&1 || fail "Docker is required."
command -v curl >/dev/null 2>&1 || fail "curl is required."
docker info >/dev/null 2>&1 || fail "Docker is not running."
[[ -x "${BACKEND_DIRECTORY}/mvnw" ]] || fail "backend/mvnw is not executable."
[[ -x "${FRONTEND_DIRECTORY}/node_modules/.bin/vitest" ]] ||
  fail "Frontend dependencies are missing. Run npm ci in frontend first."

if curl --silent --output /dev/null --connect-timeout 1 "${BACKEND_URL}"; then
  fail "Port 8081 is already serving another process."
fi

log "Starting Spring Boot with a Testcontainers PostgreSQL database"
(
  cd "${BACKEND_DIRECTORY}"
  exec ./mvnw \
    -Dspring-boot.run.main-class=es.codeurjc.infracture.TestInfractureBackendApplication \
    -Dspring-boot.run.profiles=integration-test \
    spring-boot:test-run
) >"${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!

wait_for_backend

log "Running the real frontend client and catalogue view"
(
  cd "${FRONTEND_DIRECTORY}"
  npm run test:integration
)
