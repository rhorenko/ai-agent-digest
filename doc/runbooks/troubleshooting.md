# Troubleshooting

- Check java -version and .\mvnw.cmd -version for JDK 25.
- Wrapper download: inspect .mvn/wrapper/maven-wrapper.properties and network/proxy settings.
- Configuration: copy .env.example locally; container scripts load root .env, direct Java does not. Live needs Claude model/key and Telegram token/chat; recovery-only needs Telegram credentials.
- Mongo: wait for Compose health. Host JAR uses localhost:27019, containers mongo:27017. Keep live/offline databases separate.
- Exit 3: inspect the lock. Remove only after confirming the owner stopped; see deployment.md.
- Exit 2: inspect news status/error/retryAt. Reconcile UNKNOWN before requeueing.
- No news: empty is valid. Inspect feed warnings, date/window/candidate cap and model selection. HEAD host/status and rejection logs are visible at INFO. Only a single permanent same-URL trailing-slash redirect is supported; other redirects and HEAD-rejecting article servers are excluded. Check whether old environment/IDE settings override the 20-feed/30d/two-item defaults.
- Context too small: increase DIGEST_MAX_CONTEXT_CHARS or reduce profile/excerpt size. Never bypass response validation.
- Docker unavailable: start Docker Desktop; explicitly record skipped Mongo checks if necessary.
- Immediate container exit is expected for a batch; inspect Digest status and exit code.
- Text encoding in Windows: use a UTF-8 terminal such as PowerShell 7. HTTP/XML/JSON, container output and Mongo records retain Unicode.
- English-only delivery: non-Latin titles/analysis fail validation. Older stored items with unsupported scripts become REJECTED / TELEGRAM_INVALID_MESSAGE during recovery without a Telegram request. Inspect and reconcile them manually; records are not automatically translated or deleted.
