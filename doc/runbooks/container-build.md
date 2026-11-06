# Container build

From the repository root, `docker compose -f docker/compose.yaml build app` or `.\scripts\build-container.ps1`. The build uses pinned Temurin Java 25 JDK and produces a JRE image running as non-root `app`; secrets and `.env` are excluded from the build context. The image build skips tests; run Maven verify separately. Image coordinates may be overridden with `IMAGE_NAME` and `IMAGE_TAG`. Scripts use a root `.env` when present. Compose keeps MongoDB in a named volume and waits for its health check before starting the one-shot app.
