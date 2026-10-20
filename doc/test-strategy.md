# Test strategy

## Purpose

Provide confidence proportional to product risk and trace every acceptance conclusion to executed evidence.

## Test levels

- Unit tests for deterministic plain Java behavior and boundary validation.
- Spring integration tests only when wiring, configuration, lifecycle, or framework behavior matters.
- Package and launch checks for the executable JAR.
- Container build and run checks for image behavior and process exit.
- Manual checks only where automation has lower value; record exact steps and evidence.

## Scenario design

Derive positive, negative, boundary, configuration, and regression scenarios from each feature. Prioritize irreversible data effects, external boundaries, security, error reporting, and operational recovery when those concerns exist.

## Independence

`dev` writes development tests. `qa` independently derives scenarios from requirements, assesses test quality, and may add checks without weakening expected behavior.

## Evidence and status

Map requirement/acceptance IDs in `acceptance-matrix.md`. Record commands, environment, outcome, limitations, and unrun checks in `verification-report.md`. `PASS` requires an actual run against the identified version.

## Baseline template checks

- `.\mvnw.cmd verify` (or `./mvnw verify`).
- Run the packaged JAR and confirm message and exit code 0.
- Build the image from repository-root context.
- Run the one-shot container and confirm message and exit code 0.
