# Java 25 agent-development template

A reusable Maven project for minimal Spring Boot applications and file-guided agent work. It contains no product integrations or domain logic. Roles are repository conventions, not an installed multi-agent framework.

## Prerequisites

- JDK 25
- Git
- Docker with Compose for container workflows
- PowerShell 7 or Windows PowerShell 5.1 for `scripts/*.ps1`

Maven installation is not required; use the checked-in Maven Wrapper.

## Repository layout

- `AGENTS.md` — entry point for Codex and workflow rules.
- `roles/` — sponsor, product, architecture, development, and QA instructions.
- `doc/` — product artifacts, current state, templates, evidence, and runbooks.
- `src/` — minimal one-shot Spring Boot application and tests.
- `docker/` — image and Compose definitions.
- `scripts/` — location-independent PowerShell helpers.

## Build and test

On Linux/macOS:

```bash
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
# or
.\scripts\verify.ps1
```

## Run locally

```powershell
.\scripts\run-local.ps1
```

Or package and run directly:

```powershell
.\mvnw.cmd package
java -jar .\target\agent-digest-0.0.1-SNAPSHOT.jar
```

The application prints `Application completed successfully.` and exits with code 0. Replace the demonstration `ApplicationRunner` in `AgentDigestApplication` with the concrete product entry flow. Change the execution model when a future product must be a server or long-running worker.

## Run with Docker

```powershell
.\scripts\build-container.ps1
.\scripts\run-container.ps1
```

Equivalent Compose commands from the repository root:

```powershell
docker compose -f docker/compose.yaml build
docker compose -f docker/compose.yaml run --rm app
```

See `doc/runbooks/container-build.md` and `doc/runbooks/deployment.md` for configuration, logs, exit codes, and cleanup.

## Use the roles

Examples:

- `Work as po: prepare requirements for …`
- `Work as architect: analyze feature …`
- `Work as dev: implement feature …`
- `Work as qa: verify feature …`
- `Take the task through the roles from idea to verified delivery`

The agent reads `AGENTS.md`, `doc/project-state.md`, and the selected lowercase file under `roles/`. For a full cycle it proceeds sequentially; a small task does not require every role or document.

## Create the first feature

1. Fill in the relevant product sections of `doc/project-brief.md` and `doc/requirements.md`.
2. Copy `doc/features/_template.md` to `doc/features/feat-001-short-name.md`.
3. Assign stable requirement and acceptance IDs and update `doc/backlog.md`.
4. Create `doc/plans/feat-001-plan.md` from the plan template before non-trivial implementation.
5. Link checks and actual outcomes in the acceptance matrix and verification report.

## Continue in a new chat

Ask the agent to read `AGENTS.md` and continue from `doc/project-state.md`. That file points to the active role, feature, plan, decisions, known blockers, checks, and exact next action without requiring the entire history.

## Adapt this template

- [ ] Update project name and description.
- [ ] Change Maven coordinates if the product needs different ownership.
- [ ] Rename the Java package and directories consistently.
- [ ] Rename or replace the main class and demonstration runner.
- [ ] Complete the product brief with confirmed facts.
- [ ] Add only configuration the product actually uses; update `.env.example` files.
- [ ] Set the desired container image name in `docker/.env.example` or the environment.
- [ ] Remove demonstration content after product behavior replaces it.

Detailed local, test, container, deployment, and troubleshooting guidance lives in `doc/runbooks/`.
