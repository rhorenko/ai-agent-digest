# Architecture

## Template baseline

The repository currently contains one Maven module and a non-web Spring Boot one-shot process. `AgentDigestApplication` starts a Spring context, executes an `ApplicationRunner`, closes the context, and exits normally. Spring Boot manages dependency versions; Java compilation uses release 25 without preview features. The executable JAR is produced by `spring-boot-maven-plugin`.

The baseline deliberately has no database, network endpoint, external integration, domain layers, or long-running loop. Add a component or dependency only when a feature establishes the need.

## Product context

[Describe external actors and systems after a product is defined.]

## Components and responsibilities

| Component | Responsibility | Dependencies |
|---|---|---|
| Application entry point | Start the one-shot process and manage context lifetime | Spring Boot |
| _Future component_ | _Why it exists_ | _Explicit dependencies_ |

## Data and interfaces

[Define product-owned data and boundaries. Use typed contracts and validation at external boundaries.]

## Error handling

Unhandled startup or runner failures propagate and produce a non-zero process exit. Future expected failures must have explicit semantics; do not report fictional success or silently discard exceptions.

## Configuration

The baseline has no required environment variables or secrets. Document every added setting in `.env.example`, `docker/.env.example`, and relevant runbooks without committing values.

## Testability

Use plain unit tests for ordinary Java behavior and Spring context tests only for integration that requires Spring. Link acceptance evidence through `acceptance-matrix.md`.

## Build and runtime

Build with the Maven Wrapper and run the executable JAR locally or with the multi-stage Docker image. The container is a one-shot process and exposes no ports.

## Technical constraints and risks

- Java 25 without preview features.
- Spring Boot 4.0.5 dependency management.
- One module until a confirmed requirement justifies decomposition.
- Product-specific constraints belong here or in linked ADRs.
