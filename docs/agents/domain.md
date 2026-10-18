# Domain context

Use one product vocabulary for backend and frontend. Current code and observed behavior establish implementation state; product documents and accepted decisions establish intended behavior. Memory is supplementary context.

## Read for the task

- For domain exploration or a product decision, start from the repository-root [README](../../README.md) and follow relevant product sections. Read `GLOSSARY.md` when present and relevant ADRs in `docs/adr/`. Reuse these locations; create or extend a glossary only through an explicitly requested domain workflow.
- For execution behavior, read [execution architecture](../EXECUTION_ARCHITECTURE.md) and relevant accepted ADRs. Distinguish proposals from accepted decisions and implemented code; surface conflicts before changing the agreed behavior.
- For a routine implementation or fix, use the issue's linked context and relevant code/tests. Broaden reading only when a decision or conflict needs it.

## Phase requirements

Use the phase named by the student or approved issue/spec. Read applicable requirements and scope sections for planning and functional acceptance; ask if the phase remains ambiguous.

The student supplies `docs/PHASE_<N>_GUIDE.md` (academic requirements with source/version) and `docs/PHASE_<N>_SCOPE.md` (their application to the project). Preserve the distinction between requirements, proposals and approved decisions. Surface contradictions; scope does not silently override the guide. If an input is missing, request it and pause only the work that depends on it; do not invent a replacement.

| Phase | Academic requirements | Project scope |
| --- | --- | --- |
| 3 | [Guide](../PHASE_3_GUIDE.md), testing §5.3 | [Scope](../PHASE_3_SCOPE.md), testing/evidence §§7 and 19 |

At a requested phase transition, update this index once the supplied documents exist and reconcile changed acceptance requirements. Preserve earlier evidence. Existing setup remains valid; planning workflows still require explicit invocation.
