# Repository Guidelines

## Project Structure & Module Organization

The Spring Boot modular monolith lives in `backend/`. Production code is under `backend/src/main/java/es/codeurjc/infracture`; organize features by `api`, `application`, `domain`, and `persistence`, as shown by `catalog/`. Configuration and Flyway migrations belong in `backend/src/main/resources`; tests mirror production packages in `backend/src/test/java`.

Local infrastructure is in `compose.yaml`; architecture, OpenAPI output, and images are in `docs/`. Treat the linked issue's acceptance criteria as scope. Consult `docs/EXECUTION_ARCHITECTURE.md` for execution context. Use the repository-root `README.md` as the only README source for product scope, functionality, entities, and analysis; do not use README files outside this working repository.

## Build, Test, and Development Commands

Run infrastructure commands from the repository root:

```bash
docker compose up -d --wait   # Start the development PostgreSQL service
docker compose down           # Stop it without deleting persisted data
```

Run backend commands from `backend/`:

```bash
./mvnw verify                 # Compile and run the full test suite
./mvnw spring-boot:run        # Start the API on localhost:8080
./mvnw verify -Popenapi       # Regenerate docs/api YAML and HTML
```

Keep PostgreSQL running for Spring context tests and OpenAPI generation. Use Java 21 as pinned by `.java-version`.

## Coding Style & Naming Conventions

Use four-space Java indentation and constructor injection. Package names are lowercase; classes use PascalCase; methods and fields use camelCase. Name API representations `*DTO`, data interfaces `*Repository`, and services `*Service`. Keep HTTP contracts under `/api/v1`. Name migrations `V<number>__snake_case_description.sql`; add migrations rather than editing applied ones. No formatter or linter is configured, so match neighboring code and run `git diff --check`.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot Test. Name classes `*Tests` and methods after observable behavior, such as `returnsOnlyEnabledTemplates`. Add focused tests for behavior changes. No numeric coverage threshold is configured; prioritize service rules, persistence constraints, migrations, and public APIs.

## Commit & Pull Request Guidelines

History follows Conventional Commit prefixes such as `feat:`, `fix:`, and `chore:` with short imperative summaries. Keep each pull request small and linked to its issue. Complete `.github/pull_request_template.md`: explain the outcome, list changes, report exact test and manual-verification results, and disclose risks, migrations, limitations, or follow-up work. Include screenshots for visible UI changes when a frontend is added.

## Agent-Specific Instructions

Review `AI_USAGE.md` during every work block and add one grouped entry when AI produced a material decision, result, configuration change, or notable tool use. Leave changes unstaged, and obtain the student's explicit approval before staging, committing, pushing, opening a pull request, or closing an issue.
