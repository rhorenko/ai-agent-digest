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

## Digest acceptance checks

- `.\mvnw.cmd verify` (or `./mvnw verify`).
- Run the packaged JAR offline against a separate real Mongo database; verify records, exit 0 and a deduplicated rerun.
- Build the image from repository-root context.
- Run the one-shot container offline; verify message, records and rerun. Confirm busy/unresolved/configuration exits 3/2/1.
- Use local HTTP stubs for source/Claude/Telegram contracts and Testcontainers for Mongo persistence, lock, unique index, crash recovery and the external-acceptance/database-write gap.
- No paid Claude or real Telegram calls in automated verification. Model quality remains a separate intentional live check.
