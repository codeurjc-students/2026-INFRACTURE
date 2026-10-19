# Repository Guidelines

## Scope and simplicity

Use the approved issue or selected slice as scope. The student chooses manual, paired or delegated work; guidance requests call for explanation, and delegation requires an explicit request.

Implement the smallest complete change within the existing structure. Reuse repository code, platform behavior and installed dependencies. Add an abstraction, option, special case or dependency only when a current requirement or concrete failure needs it. Preserve security, validation, accessibility and data integrity. State unresolved tradeoffs without turning every hypothetical risk into implementation work.

Finish when the agreed behavior and required evidence are complete. Keep plans and handoff in the conversation unless a document is requested or required. Retain necessary verification evidence; use temporary files for scratch work. Improve unrelated code only in separately agreed work.

## Structure and conventions

The Spring Boot modular monolith lives in `backend/`; React lives in `frontend/`. Follow the existing feature packages (`api`, `application`, `domain`, `persistence`) and controller → service → repository flow. Flyway migrations belong in `backend/src/main/resources/db/migration`; tests mirror production packages.

Use four-space Java indentation, constructor injection and descriptive English names. Keep code/comments in English and match neighboring style. Name API representations `*DTO`, repositories `*Repository`, services `*Service` and Java tests `*Tests` with observable behavior names. Keep HTTP contracts under `/api/v1`. Add migrations as `V<number>__snake_case_description.sql`; preserve applied migrations.

## Commands and tests

Use runtime pins in `.java-version` and `.nvmrc`. Commands and quality gates live in `backend/pom.xml`, `frontend/package.json`, `frontend/vitest.config.ts` and [the development guide](docs/development-guide.md).

- Infrastructure, from root: `docker compose up -d --wait`; stop with `docker compose down`, preserving data.
- Backend, from `backend/`: `./mvnw verify`; `./mvnw spring-boot:run`; regenerate OpenAPI with `./mvnw verify -Popenapi`.
- Keep required services running for checks that use them; isolated test harnesses own their disposable resources.

Use focused checks during development and applicable full suites at handoff. Add tests for missing behavior; reuse existing evidence. Preserve the independent backend/frontend 70% line-coverage gates. For functional completion, consult the academic categories in [infracture-tdd](.agents/skills/infracture-tdd/SKILL.md); reading those requirements does not start a TDD workflow. Run `git diff --check`.

## Agent workflows and context

Ponytail follows the configured default (ultra unless the student selects another level or disables it). Other engineering workflows run only when explicitly requested. Setup configures context; it does not authorize planning or implementation.

Use the local variants for requested workflows:

- [infracture-implement](.agents/skills/infracture-implement/SKILL.md): implementation, including its local TDD, review and applicable application verification procedures.
- [infracture-tdd](.agents/skills/infracture-tdd/SKILL.md): test-first development and academic evidence.
- [infracture-code-review](.agents/skills/infracture-code-review/SKILL.md): review the requested changes against repository rules and acceptance.
- [verify-infracture](.agents/skills/verify-infracture/SKILL.md): real application verification with the existing isolated harness and feature map; maintain paths affected by changed behavior.

Before tracker operations, read [issue tracker conventions](docs/agents/issue-tracker.md). Before classifying issues, read [label mappings](docs/agents/triage-labels.md). Use GitHub Issues in `codeurjc-students/2026-INFRACTURE`.

For domain decisions or phase planning, read [domain context](docs/agents/domain.md). The repository-root `README.md` links the product sources; use only this working repository's documentation. Read [execution architecture](docs/EXECUTION_ARCHITECTURE.md) when execution behavior is relevant. Routine fixes need only the context they touch.

## Communication and delivery

Reply in Spanish when the student uses Spanish. Use plain words, short sentences and consistent terms. Explain the purpose, behavior and evidence so the student can review the work and continue manually.

Use GitHub Flow with short English kebab-case branch names and Conventional Commit summaries. Integrate through PRs; never commit directly to `main`. For an authorized PR, complete [.github/pull_request_template.md](.github/pull_request_template.md) with actual checks, limits and screenshots for visible UI changes.

Apply the [changelog/release policy](docs/tracking.md#changelog-y-notas-de-versión) and [AI recording criteria](docs/AI_USAGE.md#criterio-de-registro) when the contribution qualifies. Evaluation may result in no entry. Before writing a changelog entry, read the [changelog conventions](docs/agents/changelog.md). Local agent configuration, routine Git operations and conversations without a development contribution belong in neither record.

Leave changes unstaged, preserving the existing index. Obtain the student's explicit approval before staging, committing, pushing, opening a pull request, publishing a release or closing an issue.
