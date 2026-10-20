# Verification report

Report date: 2026-10-20

## Scope

Initialization of the reusable Java 25 and Spring Boot project template. Future product verification must add feature and criterion IDs.

## Environment

- Host: Windows 11 environment
- Java: Temurin 25.0.3
- Docker client: 29.2.1
- Docker Compose: 5.1.0

## Executed checks

| Check | Command or method | Result | Evidence / notes |
|---|---|---|---|
| Spring Boot artifact availability | Maven Central request for parent `4.0.5` | PASS | HTTP 200 on 2026-10-20 |
| Build image manifest availability | `docker manifest inspect eclipse-temurin:25.0.2_10-jdk-noble` | PASS | Manifest resolved on 2026-10-20 |
| Runtime image manifest availability | `docker manifest inspect eclipse-temurin:25.0.2_10-jre-noble` | PASS | Manifest resolved on 2026-10-20 |
| Required structure | PowerShell path assertions for all requested files | PASS | No required files missing |
| PowerShell syntax | Parse all `scripts/*.ps1` with `[scriptblock]::Create` | PASS | Four scripts parsed successfully |
| Compose configuration | `docker compose -f docker/compose.yaml config --quiet` | PASS | Configuration resolved from repository root |
| Maven verification | `.\mvnw.cmd --batch-mode --no-transfer-progress verify` | PASS | Java release 25; 1 test, 0 failures, 0 errors, 0 skipped; executable JAR created |
| Packaged application launch | `java -jar .\target\agent-digest-0.0.1-SNAPSHOT.jar --verification.argument=accepted` | PASS | Expected message; exit code 0 |
| Script location independence and argument forwarding | Run `scripts/verify.ps1 -DskipTests` while current directory is `C:\` | PASS | Maven option applied; exit code 0 |
| Local-run script location independence | Run `scripts/run-local.ps1` while current directory is `C:\` | PASS | Expected message; Spring Boot run and process exit code 0 |
| Docker image build | `.\scripts\build-container.ps1` | PASS | Multi-stage image `agent-digest:local` built; tests passed in build stage |
| Container launch and argument forwarding | Run `scripts/run-container.ps1 --verification.container=true` while current directory is `C:\` | PASS | Expected message; exit code 0 |
| Runtime least privilege | `docker image inspect agent-digest:local --format '{{.Config.User}}'` | PASS | Configured user is `app`; process log also reports user `app` |
| Runtime excludes compiler | `docker run --rm --entrypoint sh agent-digest:local -c 'command -v javac || true'` | PASS | No `javac` path returned |
| Dependency scope review | `.\mvnw.cmd ... dependency:tree -Dscope=compile` | PASS | Only minimal Spring Boot starter compile graph; no product integration dependency |

## Detected limitations

- The standard Spring Boot test starter emits a Mockito dynamic-agent warning on Java 25. It does not affect the current passing test, but future JDK behavior may require adopting Mockito's documented agent configuration if mocking is used.
- Production deployment was not designed or tested; by definition this template covers local container execution only.

## Checks not executed

- Linux/macOS host execution of `./mvnw verify`; the same wrapper ran inside the Linux build container and Windows wrapper ran on the host.
- Production deployment checks, because production deployment is outside template scope.

## Overall status

PASS for the requested reusable template scope. The listed limitations do not block local Maven, JAR, or Docker use.
