# Agent development instructions

## Repository purpose

This repository is a reusable Java 25 and Spring Boot template. It provides a minimal one-shot application plus a file-based workflow from an idea to verified delivery. The files in `roles/` are working agreements, not BMAD, built-in Codex commands, or a multi-agent runtime.

## Context loading order

Do not load all documentation automatically. At the start of work:

1. Read this file.
2. Read `doc/project-state.md`.
3. Read only the active role in `roles/`.
4. Read the feature, plan, architecture, ADR, and verification material needed for the current task.

Read completed plans, old features, and long decision history only when relevant.

## Key files

- `README.md`: template usage and commands.
- `roles/`: stable instructions for `ceo`, `po`, `architect`, `dev`, and `qa`.
- `doc/project-state.md`: compact current-state handoff.
- `doc/features/`: product features with stable acceptance IDs.
- `doc/plans/`: implementation plans.
- `doc/adr/`: significant architecture decisions.
- `doc/acceptance-matrix.md`: traceability from criteria to evidence.
- `doc/verification-report.md`: checks that were actually run.
- `doc/runbooks/`: operational commands and troubleshooting.
- `src/`: application and tests.
- `docker/` and `scripts/`: container and Windows automation.

## Role selection

Use the role named by the user. Phrases such as “Work as po: ...” are repository conventions: read `roles/po.md` and follow it. They are not native Codex commands.

- `ceo`: clarify problem, audience, value, constraints, MVP, and success.
- `po`: define requirements, scenarios, acceptance criteria, features, and backlog.
- `architect`: choose minimal architecture and record significant decisions.
- `dev`: implement an agreed task and development tests.
- `qa`: independently verify requirements and report evidence.

Default to sequential role execution. Do not start subagents unless the user explicitly requests them. For a full-cycle request, move to the next role after the current role's completion criteria are met; do not request permission for routine edits or checks. For a small concrete task, select the smallest useful role and artifact set instead of forcing the full workflow.

## Development process

1. Establish the idea and constraints in `doc/project-brief.md` when needed.
2. Define requirements and a numbered feature with acceptance IDs.
3. Confirm architecture and create ADRs only for consequential decisions.
4. Create a feature plan before non-trivial implementation.
5. Implement focused changes and development tests.
6. Hand the result to `qa` for independent requirement-based checks.
7. Run the relevant build, package, container, and launch checks.
8. Update traceability, verification evidence, and current state.

Use a separate handoff file only when it adds durable value. Otherwise update `doc/project-state.md` briefly.

## File-change rules

- Preserve Maven coordinates, package names, and useful settings unless the task requires a change.
- Keep project documentation under `doc/`; do not create `docs/` or `documentation/`.
- Keep stable role instructions only in `roles/`; never put mutable project state there.
- Make minimal, coherent changes; do not add dependencies or abstraction “for later”.
- Keep secrets and local `.env` files out of Git. Update examples and runbooks when configuration changes.
- Do not rewrite requirements to fit an implementation. Escalate material contradictions to the responsible role.

## Testing rules

- Derive checks from requirements and acceptance criteria, including positive, negative, and boundary cases.
- Prefer fast unit tests for plain Java logic. Start Spring only when integration with Spring is what the test verifies.
- Do not weaken tests to make a change pass and do not record `PASS` without executing the check.
- Run the narrowest relevant checks first, then `./mvnw verify` (`.\mvnw.cmd verify` on Windows) before completion when the environment permits.
- QA independently assesses coverage and evidence rather than relying only on the developer report.

## State updates

Keep `doc/project-state.md` short and current. Replace stale state instead of appending an endless diary. Record actual checks in `doc/verification-report.md` and maintain requirement-to-check links in `doc/acceptance-matrix.md`. Git and code remain the source of truth for exact changes.

## Completion criteria

A task is complete when applicable acceptance criteria are met, relevant tests pass, the package/run path works, documentation and state are current, limitations and unrun checks are explicit, and QA evidence is recorded for work requiring acceptance.

Commands and operational detail are in `README.md` and `doc/runbooks/`.
