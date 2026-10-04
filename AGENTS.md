# Repository Guidelines

## Project Structure & Module Organization

The Spring Boot modular monolith lives in `backend/`. Production code is under `backend/src/main/java/es/codeurjc/infracture`; organize features by `api`, `application`, `domain`, and `persistence`, as shown by `catalog/`. Configuration and Flyway migrations belong in `backend/src/main/resources`; tests mirror production packages in `backend/src/test/java`.

Local infrastructure is in `compose.yaml`; architecture, OpenAPI output, and images are in `docs/`. Treat the linked issue's acceptance criteria as scope. Consult `docs/EXECUTION_ARCHITECTURE.md` for execution context. The repository-root `README.md` is the public entry point to the Phase 1 sections under `docs/`; those sections are the sources for product scope, functionality, entities, and analysis. Do not use README files outside this working repository.

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

Keep PostgreSQL running for Spring context tests and OpenAPI generation. Use Java 25 LTS as pinned by `.java-version`.

## Coding Style & Naming Conventions

Use four-space Java indentation and constructor injection. Package names are lowercase; classes use PascalCase; methods and fields use camelCase. Name API representations `*DTO`, data interfaces `*Repository`, and services `*Service`. Keep HTTP contracts under `/api/v1`. Name migrations `V<number>__snake_case_description.sql`; add migrations rather than editing applied ones. No formatter or linter is configured, so match neighboring code and run `git diff --check`.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot Test. Name classes `*Tests` and methods after observable behavior, such as `returnsOnlyEnabledTemplates`. Add focused tests for behavior changes. Backend and frontend each enforce a 70% line coverage threshold; consult `backend/pom.xml` and `frontend/vitest.config.ts` for the current gates. Prioritize service rules, persistence constraints, migrations, and public APIs.

## Commit & Pull Request Guidelines

History follows Conventional Commit prefixes such as `feat:`, `fix:`, and `chore:` with short imperative summaries. Keep each pull request small and linked to its issue. Complete `.github/pull_request_template.md`: explain the outcome, list changes, report exact test and manual-verification results, and disclose risks, migrations, limitations, or follow-up work. Include screenshots for visible UI changes when a frontend is added.

Use GitHub Flow: keep `main` stable and ready to deploy, never commit directly to it, and integrate changes through pull requests. Name working branches with a short, descriptive English kebab-case name, such as `add-login-page`, `fix-login-page`, or `add-ci-workflow`; no mandatory `<type>/` or `codex/` prefix is used.

## Agent skills

Invoke engineering skills only when the student explicitly requests them. Setup configures their context; it does not start planning or implementation.

### Issue tracker

Use GitHub Issues in `codeurjc-students/2026-INFRACTURE`. Before tracker operations, read [issue tracker conventions](docs/agents/issue-tracker.md).

### Triage labels

Use the five default triage roles. Before classifying issues, read [label mappings](docs/agents/triage-labels.md). Readiness does not authorize delegation.

### Domain docs

Use a single domain context and the existing documentation locations. Before domain exploration or phase planning, read [domain context](docs/agents/domain.md).

### TFG implementation workflow

For implementation, TDD and code review in this repository, use the local variants:

- [infracture-implement](.agents/skills/infracture-implement/SKILL.md)
- [infracture-tdd](.agents/skills/infracture-tdd/SKILL.md)
- [infracture-code-review](.agents/skills/infracture-code-review/SKILL.md)

An explicit request to run `infracture-implement` includes its linked local TDD, review and `verify-infracture` procedures, including maintenance of affected verification paths. The student chooses manual, paired or delegated work for each issue or selected slice. Keep each issue understandable and executable manually. Hand over unstaged changes, preserving any pre-existing index; the student makes the commit. Follow the delivery controls below for other Git/GitHub actions.

### Application verification

When the student requests real application verification directly or through `infracture-implement`, use [verify-infracture](.agents/skills/verify-infracture/SKILL.md) and its maintained feature map. It reuses the isolated browser/API harness and retains evidence; it complements the academic test requirements.

## Agent-Specific Instructions

### Explanations and human understanding

Make the work understandable so the student can review decisions and continue implementation manually. These communication requirements apply proactively; the student should not have to request them each time.

- **Clear text is required.** Use plain, concrete language, short sentences and consistent terminology, inspired by a flexible ASD-STE100 style rather than claiming formal compliance. Reply in Spanish when the student uses Spanish. Explain unfamiliar terms and connect the purpose, behavior and evidence before adding implementation detail.
- **Use diagrams for structural explanations.** When explaining architecture, relationships, process flows or interactions across components, include a diagram that makes those connections visible. Use the actual project names and distinguish implemented behavior from proposals.
- **Use interactive HTML for exploration.** When understanding depends on changing inputs, comparing scenarios or following state changes, provide a focused interactive HTML explanation or visualization. Keep it separate from production code unless its integration is requested. Make controls and outputs understandable, and label simulated data and assumptions.

Choose the simplest format that explains the subject well. Simple factual answers and routine status updates can remain text-only; a substantial explanation should not default to a wall of text when a diagram or interactive view would clarify it. These artifacts support human understanding and complement verification; they do not replace tests or evidence.

### Traceability and delivery

Review `docs/AI_USAGE.md` during every work block and add one grouped entry when AI produced a material decision, result, configuration change, or notable tool use. Leave changes unstaged, and obtain the student's explicit approval before staging, committing, pushing, opening a pull request, or closing an issue.

Review `CHANGELOG.md` during every work block and update `Unreleased` when the work introduces a relevant change. Record only changes already incorporated into the repository, identify documentation and decisions explicitly, and never present planned functionality as implemented.
