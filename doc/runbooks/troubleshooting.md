# Troubleshooting

- Verify Java and Maven versions with `java -version` and `.\mvnw.cmd -version`.
- Missing settings produce a nonzero startup exit; see `.env.example` and README. A local Java process needs exported environment variables; Compose can read the root `.env` via `--env-file`.
- A nonzero batch exit can indicate feed, model, MongoDB or Telegram failure, or unresolved delivery. Inspect logs and persisted states without exposing credentials.
- `SENDING` and `AMBIGUOUS` require human review before retry. A stale batch lock also requires checking no job is active. Follow the deployment runbook; do not delete data or volumes.
- If Docker is unavailable, start Docker Desktop and check `docker version`. MongoDB Testcontainers and container tests must be reported as unverified until they run.
- Feed links requiring redirects, HEAD-disabled pages, undated items and timestamps outside the window are excluded. Default source coverage is limited to GitHub AI and ML.
