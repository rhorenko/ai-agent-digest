# Container build

## Build context

The Docker build context is the repository root so `.dockerignore` is applied and Maven sources are available. Compose resolves this correctly even though its file is under `docker/`.

## Build with Compose

From the repository root:

```powershell
docker compose -f docker/compose.yaml build
# or
.\scripts\build-container.ps1
```

Copy `docker/.env.example` to `docker/.env` to customize `IMAGE_NAME` and `IMAGE_TAG`, or set those environment variables before running Compose. No secrets are build arguments or image content.

## Direct build

```powershell
docker build -f docker/Dockerfile -t agent-digest:local .
```

The first stage builds with pinned Temurin JDK 25.0.2; the final image uses the pinned Temurin JRE 25.0.2, contains no compiler or Maven, and runs as user `app`.
