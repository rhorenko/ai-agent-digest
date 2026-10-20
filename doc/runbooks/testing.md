# Testing

## Standard verification

```powershell
.\mvnw.cmd verify
# or from any directory
& '<repository>\scripts\verify.ps1'
```

On Linux/macOS use `./mvnw verify`. The Maven `verify` lifecycle compiles with Java release 25, runs JUnit Jupiter tests, and builds the executable Spring Boot JAR.

## Focused checks

Run one test while developing:

```powershell
.\mvnw.cmd -Dtest=AgentDigestApplicationTests test
```

Prefer plain unit tests for ordinary logic. Use `@SpringBootTest` only when Spring wiring or lifecycle is part of the behavior under test.

## Acceptance evidence

Map feature acceptance IDs to checks in `doc/acceptance-matrix.md`. Record exact commands and actual results in `doc/verification-report.md`. Include negative and boundary scenarios. Never infer PASS from code review alone.
