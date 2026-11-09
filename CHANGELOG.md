# Changelog

This file summarizes the notable changes in each Infracture Local version for the people who use it, install it or consume its API. No version has been published yet, so everything delivered is under `Unreleased`.

The format follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/). The criteria for deciding and writing each entry are in [Seguimiento](docs/tracking.md#changelog-y-notas-de-versión).

## [Unreleased]

### Added

- User accounts: sign up, sign in and sign out. Signing up signs you in automatically.
- A session lasts 7 days and the web app renews it on its own in the meantime. Restarting the backend ends all sessions.
- Account page at `/account` with the name, username and email.
- Private projects at `/projects`: each account creates, lists and edits its own, and nobody else can see them. They cannot be deleted or published yet.
- Scenarios inside each project, with an editable name and description. They cannot be deleted yet.
- Public catalogue at `/catalogue` with the HTTP Service, Worker, PostgreSQL, Redis and RabbitMQ templates, with search by name and a filter by type.
- Scenario canvas: add up to 50 catalogue components with a click or by dragging them, and configure each one with a form that only accepts values within its type's limits.
- Connections on the canvas: drag from a component's port to a component that offers what the port needs, such as Redis for a cache. Each arrow means "depends on", shows the port's label and survives a profile change, marked until you remove it.
- An issues panel lists what the last save found: errors, such as a missing connection, a dependency cycle or an empty scenario, which will block running, and warnings, which do not. Picking one selects its component.
- The scenario is saved as a whole. If another tab saved first, the editor warns without overwriting anything and lets you choose which version to keep.
- Unsaved work is kept in the browser and can be recovered after a reload or after the session expires. Another account in the same browser cannot see it.
- Run and Stop on the scenario canvas, for scenarios whose nodes are HTTP Services in `STATELESS` mode. Run starts the saved revision in Docker and Stop removes everything it created; the API offers the same under `…/scenarios/{id}/executions`.
- A scenario can carry one constant HTTP load, set in the canvas Load panel and drawn as traffic entering its target. Run starts it with k6 once every service is healthy; Stop halts it first and the panel shows its summary.
- While an experiment runs, the canvas shows its state, each node's health and the service's real response, and the scenario is read-only. One experiment runs at a time per installation; a node that falls over fails it, and it stops after 30 minutes.
- Light or dark theme following the system preference.
- REST API under `/api/v1` for all of the above. Requests that change data must send the `X-XSRF-TOKEN` header, obtained with `GET /api/v1/auth/csrf`, and errors arrive as `application/problem+json`. The OpenAPI contract and the Postman collection are in [`docs/api`](docs/api).
- A single administrator created on first start. Before starting, copy `.env.example` to `.env` in the repository root and fill it in, or define the `INFRACTURE_ADMIN_*` variables.
- `docker compose --profile app up -d --wait --build` runs the backend in a container that reaches Docker only through a restricted proxy. Build the demo and load images first with `docker build -t infracture/demo-app:0.1.0 demo-app` and `docker build -t infracture/load-generator:0.1.0 load-generator`; see the [development guide](docs/development-guide.md).
- Local run with `scripts/start-dev.sh`, which starts PostgreSQL in Docker Compose, the backend and the frontend. It requires Java 25 and Node.js 24 at the exact versions in `.java-version` and `.nvmrc`; the steps are in the [development guide](docs/development-guide.md).
