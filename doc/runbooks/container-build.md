# Container build

Existing multi-stage Dockerfile: Temurin JDK 25.0.2 builds the executable JAR; JRE 25.0.2 runs as non-root app. Context is repository root; .dockerignore excludes local dotenv files.

~~~powershell
.\mvnw.cmd verify
.\scripts\build-container.ps1
# Equivalent:
docker compose -f docker/compose.yaml build
~~~

Host verify runs all tests including Mongo Testcontainers. Image packaging uses -DskipTests, avoiding Docker-in-Docker. Image build success alone is not test evidence. Only the JAR enters the runtime stage; no compiler, Maven, dotenv or build secrets.

IMAGE_NAME/IMAGE_TAG remain configurable. Container scripts optionally load root .env; direct Compose can use --env-file .env. Do not pass secrets as build arguments. Start Mongo with docker compose -f docker/compose.yaml up -d --wait mongo. The application has a readiness dependency, persistent volume, no application ports and no restart loop.
