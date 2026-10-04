# Issue tracker: GitHub

Issues and published specs live in GitHub Issues for `codeurjc-students/2026-INFRACTURE`. Use the `gh` CLI from this repository; confirm the target against `git remote -v`.

## Read and prepare

- Read a ticket with `gh issue view <number> --comments`; include its body, labels, acceptance criteria and relevant discussion.
- List tickets with `gh issue list --state open --json number,title,body,labels`, narrowing by labels or milestone as appropriate.
- Resolve an ambiguous issue/PR number before acting; GitHub shares their number space.
- Preserve existing issue templates, hierarchy and project conventions. Each implementation ticket must state scope, acceptance, verification and dependencies, and remain executable manually.

## Publish and update

A skill's instruction to publish to the tracker means creating a GitHub issue once the student has authorized publication. Present reviewable content before requesting any missing authorization. An already authorized action needs no repeated confirmation.

- Create issues with `gh issue create --title "..." --body-file <file>`.
- Publish an explicitly requested comment with `gh issue comment <number> --body-file <file>`.
- Apply approved label changes with `gh issue edit <number> --add-label "..."` or `--remove-label "..."`; use [the mappings](triage-labels.md).
- Use files with actual newlines for multiline bodies. Keep publication separate from drafting and implementation.

The setup grants no permission to create issues or labels, comment, assign work, close issues or change Project status. Obtain authorization for those writes when needed. Staging, commits, pushes, PR creation, merges and closure remain under the student's explicit control. A completed slice does not imply the parent issue is complete.

## Dependencies and optional wayfinding

When an explicitly invoked skill needs parent/child tickets, reuse the existing hierarchy. Prefer native sub-issues and issue dependencies when supported. Otherwise use a parent task list, `Part of #<number>` in children and `Blocked by: #<number>` references. Confirm blockers are resolved before selecting dependent work. Assignment and resolution are authorized tracker writes, not automatic consequences of reading or selecting a ticket.

## Pull requests as a triage surface

**PRs as a request surface: no.**
