# Local development

Use JDK 25 and the Maven Wrapper: `.\mvnw.cmd verify`. The executable JAR runs a single batch and exits. It reads environment variables or Spring Boot properties; the local Java process does not automatically load `.env`. Start a local MongoDB, set `MONGODB_URI`, `MONGODB_DATABASE`, and either set `DIGEST_OFFLINE=true` for fixtures or configure live credentials. Use a separate database for the demo. See the root README for all settings. `scripts/run-local.ps1` runs the same Spring Boot entry point.
