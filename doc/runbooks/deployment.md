# Deployment

In this template, **deployment means local container execution only**. The repository does not claim a production deployment design, registry publication, orchestration, monitoring, backup, security hardening for a specific environment, or rollback automation.

## Run and observe

```powershell
docker compose -f docker/compose.yaml run --rm app
# or
.\scripts\run-container.ps1
```

Compose streams the one-shot process logs to the terminal. The expected output contains `Application completed successfully.`. Inspect the shell exit code immediately afterward:

```powershell
$LASTEXITCODE
```

The expected exit code is `0`. No ports, health check, or restart policy are defined because the baseline is not a service.

## Configuration

Set only variables that the adapted product actually consumes. Add them to the Compose service explicitly and document non-secret examples. Never place credentials in the Dockerfile, image, or committed `.env` file.

## Cleanup

`compose run --rm` removes its stopped container. Remove any remaining Compose resources and the local image with:

```powershell
docker compose -f docker/compose.yaml down --remove-orphans
docker image rm agent-digest:local
```

Review the target image name before removal when `IMAGE_NAME` or `IMAGE_TAG` is customized.
