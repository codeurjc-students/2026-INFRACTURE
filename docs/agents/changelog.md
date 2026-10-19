# Changelog conventions

`CHANGELOG.md` follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/) and the policy in [Seguimiento](../tracking.md#changelog-y-notas-de-versión). Entries are written in Spanish.

## Decide whether it belongs

An entry exists only when you can answer: who notices the change (a user, an installer or an API consumer), how they notice it, and whether they must do something. No answer, no entry; say so in the PR's Changelog section.

- Compare with the last published release, never with `main` or the branch. Describe work added and modified before a release once, in its final state. While no release exists, everything goes under `Added`.
- `Changed`, `Fixed` and `Removed` only refer to something a published release contained. `Security` is for fixed vulnerabilities; a new security feature is `Added`.
- Leave out tests, CI, coverage, refactors, dependency updates that keep the requirements, bugs introduced and fixed before a release, plans, tickets, AI activity, personal configuration and routine Git. PRs, the spec, ADRs and `docs/AI_USAGE.md` record them.

## Write it

- One bullet per visible result; merge adjustments that deliver the same result.
- Start with what the reader gets, then its effect, using the names they see: routes, endpoints, variables and commands.
- Include what the reader must do or know: migration steps, installation requirements and known limits.
- Skip internals (classes, libraries, migration numbers) unless the reader needs them to act.
- Short sentences in the present tense. An issue or PR link may accompany an entry but never replaces the explanation.
- Before requesting review, check every statement against the branch's code.
