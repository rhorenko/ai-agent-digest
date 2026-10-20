# Project state

Last updated: 2026-10-21

## Stage

Template ready for product adaptation.

## Current goal

Deliver a reusable Java 25, Spring Boot 4.0.5 Maven template with role instructions, documentation, tests, Docker, and Windows scripts.

## Active role

None. Select a role from the next product task.

## Active feature and plan

No product feature or product plan exists; this work initializes the template itself.

## Completed work

- Existing Maven coordinates and Java package inspected and retained.
- Root workflow, all role files, documentation templates, runbooks, Java baseline, Docker files, and Windows scripts created.
- Independent verification covered structure, Maven, JAR, scripts, image contents, and container execution.

## Blockers

None currently known.

## Open questions

None required to finish the requested template.

## Accepted assumptions

- The existing coordinates `com.agents.digest:agent-digest` and package `com.agents.digest` remain the adaptation starting point.
- Deployment in this template means local container execution only.

## Performed checks

- Spring Boot 4.0.5 artifact availability confirmed in Maven Central.
- Temurin Java 25.0.2 JDK and JRE container manifests confirmed available.
- `.\mvnw.cmd verify`: PASS; 1 test, 0 failures/errors/skips.
- Executable JAR launch: PASS; expected message and exit code 0.
- PowerShell script invocation outside the repository: PASS.
- Compose configuration and Docker image build: PASS.
- Container launch: PASS; expected message, exit code 0, user `app`, no `javac` in runtime image.

## Exact next action

Adapt `doc/project-brief.md` for the first concrete product, then ask `po` to create `doc/features/feat-001-short-name.md`.

## Current artifacts

- `AGENTS.md`
- `README.md`
- `doc/architecture.md`
- `doc/verification-report.md`
- `roles/`
