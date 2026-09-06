#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIRECTORY="${REPOSITORY_ROOT}/backend"
FRONTEND_DIRECTORY="${REPOSITORY_ROOT}/frontend"

REQUIRED_JAVA_VERSION="$(tr -d '[:space:]' < "${REPOSITORY_ROOT}/.java-version")"
REQUIRED_NODE_VERSION="$(tr -d '[:space:]' < "${REPOSITORY_ROOT}/.nvmrc")"

BACKEND_PID=""
FRONTEND_PID=""
STARTED_DATABASE=false
JAVA_HOME_FOR_PROJECT=""
USE_FNM=false

log() {
  printf '[infracture] %s\n' "$1"
}

fail() {
  printf '[infracture] Error: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

java_version_from_home() {
  "${1}/bin/java" -version 2>&1 | awk -F '"' 'NR == 1 { print $2 }'
}

select_java_home() {
  local candidate=""

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    candidate="${JAVA_HOME}"
    if [[ "$(java_version_from_home "${candidate}")" == "${REQUIRED_JAVA_VERSION}" ]]; then
      JAVA_HOME_FOR_PROJECT="${candidate}"
      return
    fi
  fi

  if [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
    candidate="$(/usr/libexec/java_home -v "${REQUIRED_JAVA_VERSION}" 2>/dev/null || true)"
    if [[ -n "${candidate}" ]] &&
      [[ "$(java_version_from_home "${candidate}")" == "${REQUIRED_JAVA_VERSION}" ]]; then
      JAVA_HOME_FOR_PROJECT="${candidate}"
      return
    fi
  fi

  fail "Java ${REQUIRED_JAVA_VERSION} is required. Set JAVA_HOME to that installation."
}

select_node_runner() {
  local current_version=""

  if command -v node >/dev/null 2>&1; then
    current_version="$(node --version)"
    current_version="${current_version#v}"
  fi

  if [[ "${current_version}" == "${REQUIRED_NODE_VERSION}" ]]; then
    require_command npm
    return
  fi

  if command -v fnm >/dev/null 2>&1; then
    if [[ "$(fnm exec --using "${REQUIRED_NODE_VERSION}" node --version 2>/dev/null)" == "v${REQUIRED_NODE_VERSION}" ]]; then
      USE_FNM=true
      return
    fi
  fi

  fail "Node.js ${REQUIRED_NODE_VERSION} is required. Activate it before running this script."
}

wait_for_backend() {
  local attempts=0
  local maximum_attempts=60

  log "Waiting for Spring Boot at http://localhost:8080/actuator/health"

  until curl --fail --silent --show-error \
    http://localhost:8080/actuator/health >/dev/null 2>&1; do
    if ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
      wait "${BACKEND_PID}" || true
      fail "Spring Boot stopped before becoming healthy."
    fi

    attempts=$((attempts + 1))
    if ((attempts >= maximum_attempts)); then
      fail "Spring Boot did not become healthy within ${maximum_attempts} seconds."
    fi

    sleep 1
  done
}

wait_for_frontend() {
  local attempts=0
  local maximum_attempts=30

  log "Waiting for Vite at http://localhost:5173"

  until curl --fail --silent --show-error \
    http://localhost:5173 >/dev/null 2>&1; do
    if ! kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; then
      wait "${FRONTEND_PID}" || true
      fail "Vite stopped before becoming ready."
    fi

    attempts=$((attempts + 1))
    if ((attempts >= maximum_attempts)); then
      fail "Vite did not become ready within ${maximum_attempts} seconds."
    fi

    sleep 1
  done
}

cleanup() {
  local exit_code=$?

  trap - EXIT INT TERM
  set +e

  if [[ -n "${FRONTEND_PID}" ]] && kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; then
    log "Stopping Vite"
    kill "${FRONTEND_PID}" >/dev/null 2>&1
    wait "${FRONTEND_PID}" >/dev/null 2>&1
  fi

  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    log "Stopping Spring Boot"
    kill "${BACKEND_PID}" >/dev/null 2>&1
    wait "${BACKEND_PID}" >/dev/null 2>&1
  fi

  if [[ "${STARTED_DATABASE}" == true ]]; then
    log "Stopping PostgreSQL without deleting its volume"
    (cd "${REPOSITORY_ROOT}" && docker compose down)
  fi

  exit "${exit_code}"
}

handle_signal() {
  exit 130
}

trap cleanup EXIT
trap handle_signal INT TERM

require_command docker
require_command curl
select_java_home
select_node_runner

[[ -x "${BACKEND_DIRECTORY}/mvnw" ]] || fail "backend/mvnw is not executable."
[[ -d "${FRONTEND_DIRECTORY}/node_modules" ]] ||
  fail "Frontend dependencies are missing. Run npm install in frontend first."

docker info >/dev/null 2>&1 || fail "Docker is not running. Start Docker Desktop and try again."

if (cd "${REPOSITORY_ROOT}" &&
  docker compose ps --status running --services | grep -qx 'platform-postgres'); then
  log "PostgreSQL is already running"
else
  log "Starting PostgreSQL"
  STARTED_DATABASE=true
  (cd "${REPOSITORY_ROOT}" && docker compose up -d --wait)
fi

log "Starting Spring Boot with Java ${REQUIRED_JAVA_VERSION}"
(
  cd "${BACKEND_DIRECTORY}"
  exec env JAVA_HOME="${JAVA_HOME_FOR_PROJECT}" ./mvnw spring-boot:run
) &
BACKEND_PID=$!

wait_for_backend

log "Starting Vite with Node.js ${REQUIRED_NODE_VERSION}"
(
  cd "${FRONTEND_DIRECTORY}"
  if [[ "${USE_FNM}" == true ]]; then
    exec fnm exec --using "${REQUIRED_NODE_VERSION}" npm run dev -- --host 127.0.0.1
  else
    exec npm run dev -- --host 127.0.0.1
  fi
) &
FRONTEND_PID=$!

wait_for_frontend

log "Application ready"
log "Frontend: http://localhost:5173"
log "Backend:  http://localhost:8080"
log "Press Ctrl+C to stop the application"

while kill -0 "${BACKEND_PID}" >/dev/null 2>&1 &&
  kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; do
  sleep 1
done

if ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
  wait "${BACKEND_PID}"
else
  wait "${FRONTEND_PID}"
fi
