# PowerShell helpers

Scripts resolve the repository root, preserve the caller directory, forward arguments and propagate failures.

| Script | Behavior |
|---|---|
| verify.ps1 | Maven verify including real Mongo Testcontainers |
| run-local.ps1 | Maven spring-boot:run using process environment |
| build-container.ps1 | Compose build; loads root .env if present |
| run-container.ps1 | Compose run --rm app; Mongo readiness; loads root .env |

~~~powershell
.\scripts\verify.ps1
.\scripts\build-container.ps1
.\scripts\run-container.ps1 --digest.offline=true --digest.database=agent_digest_offline
~~~

For run-local use a Maven property such as '-Dspring-boot.run.arguments=--digest.offline=true --digest.database=agent_digest_offline --digest.mongo-uri=mongodb://localhost:27019'.

Live runs make paid Claude calls and send real Telegram messages. Configure locally and run intentionally; never pass secrets as command arguments.
