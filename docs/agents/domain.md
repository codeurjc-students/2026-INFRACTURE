# Domain context

Use a single domain context for Infracture. Backend and frontend share the product vocabulary; they do not require separate glossary trees.

## Before exploring the domain

1. Read the repository-root [README](../../README.md) and its linked product documentation for scope, functionality, entities and analysis. Use only sources inside this working repository.
2. If the root [GLOSSARY.md](../../GLOSSARY.md) exists, read it and use its terms consistently in issues, code and tests. If absent, proceed with the existing product terminology; create or extend a glossary only when an explicitly invoked domain workflow resolves terms. Avoid duplicate glossaries.
3. Read the relevant decisions in `docs/adr/` and [execution architecture](../EXECUTION_ARCHITECTURE.md) when the subject concerns execution. Preserve existing locations and surface conflicts with accepted decisions explicitly.
4. Inspect current code, tests and Git changes to distinguish documented plans from implemented behavior. Engram supplies supplementary context; current project sources and evidence govern decisions.

## Planning across project phases

The setup and local skills apply throughout the project. For each planning or implementation request, identify the phase from the student or the approved issue/spec. Ask if it remains ambiguous; do not assume every task belongs to the latest phase.

The student prepares and supplies the two phase documents. Skills consume these inputs; they do not create or rewrite them as part of setup or a phase transition:

- `docs/PHASE_<N>_GUIDE.md`: a faithful Markdown copy of the supplied academic requirements, with source/version and relevant section references. Distinguish transcription uncertainty from actual requirements.
- `docs/PHASE_<N>_SCOPE.md`: the project's application of those requirements, including scope, acceptance, evidence, dependencies and unresolved decisions. Distinguish proposals from approved decisions and implemented behavior.

Use the phase's requirements and scope together with the README-linked product documentation, relevant ADRs and current code/tests. Surface contradictions rather than silently treating the scope as an override of academic requirements. If either phase document is missing, ask the student to supply it and pause work that depends on it. Do not generate a replacement or infer its requirements.

Preserve earlier phase documents and evidence. At a requested phase transition, once the student has supplied the new pair, update the reference table below and review the local skills for changed acceptance or testing requirements. Keep existing quality and regression controls unless an explicit, justified decision changes them. The setup does not need to run again merely because the phase changes.

| Phase | Academic requirements | Project scope |
| --- | --- | --- |
| 3 | [Guide](../PHASE_3_GUIDE.md), testing §5.3 | [Scope](../PHASE_3_SCOPE.md), testing/evidence §§7 and 19 |

This table indexes available phase-specific planning pairs; it is not an inventory of all historical project documentation. Add further rows only when the actual documents exist.

Planning skills remain explicitly invoked. Updating this configuration does not run `grill-with-docs`, `to-spec`, `to-tickets` or implementation.
