# Agentic News Digest

A Java 25 / Spring Boot 4.0.5 one-shot job that reads recent RSS/Atom metadata, asks Claude to select and analyze it, stores the results in MongoDB, and sends one Telegram message per selected item. The job closes after one batch; schedule it externally if desired.

## Build and verify

```powershell
.\mvnw.cmd verify
# Linux/macOS: ./mvnw verify
```

The test suite uses HTTP stubs and MongoDB Testcontainers. Docker is required for the MongoDB test; when unavailable that test is skipped. No test calls Claude or Telegram. The executable JAR is `target/agent-digest-0.0.1-SNAPSHOT.jar`.

## Offline demo with Docker

```powershell
docker compose -f docker/compose.yaml build app
docker compose -f docker/compose.yaml run --rm -e DIGEST_OFFLINE=true -e MONGODB_DATABASE=agent_digest_demo app
```

This runs versioned source and analysis fixtures through the same validation, MongoDB and delivery pipeline. Messages print to stdout instead of going to Telegram. Use a separate demo database: fixture records are persisted as `SENT` and must not be mixed with live records. Run the command again to exercise deduplication. MongoDB runs in a named persistent volume; do not delete it to reset a demo.

## Configure a live run

Copy `.env.example` to an untracked `.env` and edit it locally, not in chat. Set `ANTHROPIC_API_KEY`, `CLAUDE_MODEL`, `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`. Then run:

```powershell
docker compose --env-file .env -f docker/compose.yaml run --rm app
# or, after creating .env: .\scripts\run-container.ps1
```

Docker Compose defaults `MONGODB_URI` to `mongodb://mongo:27017`. For a locally installed MongoDB and local JAR, export the variables in your shell or IDE; the JAR does not automatically load `.env`:

```powershell
$env:MONGODB_URI = 'mongodb://localhost:27017'
$env:MONGODB_DATABASE = 'agent_digest'
# Set the credentials locally in your shell or IDE, then:
java -jar .\target\agent-digest-0.0.1-SNAPSHOT.jar
```

Do not expose the bundled unauthenticated MongoDB service beyond its private Compose network. For other deployments provide an appropriately secured `MONGODB_URI` and manage backups and access separately. Secrets are never needed for offline mode.

## Sources, model and limits

The only default feed is GitHub's official AI and ML feed. Set `DIGEST_FEEDS` to a comma-separated list of HTTPS RSS or Atom feeds to widen coverage. This does not cover every Claude, Codex or BMAD announcement. The application checks that an original HTTPS link returns a successful HEAD response; inaccessible, redirected, undated, stale or future-dated entries are excluded. Feeds may supply only headlines and excerpts; full article text is not fetched, and summaries must disclose that limitation. The offline fixture uses a synthetic URL and does not perform network verification.

Set `DIGEST_WINDOW_HOURS` (default 24), `DIGEST_MAX_ITEMS` (5), `DIGEST_MAX_CANDIDATES` (30), `DIGEST_TIMEOUT_SECONDS` (30), `DIGEST_MAX_OUTPUT_TOKENS` (2048), and `DIGEST_TELEGRAM_RETRIES` (2). Adjust the profile by setting `DIGEST_PROFILE` to a Spring resource such as `file:/absolute/path/profile.txt`; mount the file into a container if needed. The bundled versioned prompt, JSON schema and profile are in `src/main/resources/prompts/`. Feed content is treated as untrusted data. The model may select only local candidate IDs; Java validates the entire response before persisting its analysis.

There was no `spring-claude` dependency in the checked-in POM or resolved dependency tree. The implementation uses Anthropic's documented Messages HTTP API with JSON schema output, rather than assuming that ordinary model calls have web search. See [ADR 0001](doc/adr/0001-source-bound-analysis.md). The live provider response and selection quality have not been verified with a paid call.

## Recovery and delivery semantics

A unique normalized URL prevents duplicates. Saved `PENDING` and eligible `FAILED` records are sent in descending score order, including previously persisted items; a rerun does not resend `SENT` records. `retry_after` is persisted when a Telegram rate-limit wait exceeds the bounded in-process retries. A rejected or unavailable feed/model does not prevent retrying existing pending records, but the batch still exits nonzero.

A network timeout, 5xx response, malformed success response, or process crash after marking `SENDING` has an **ambiguous** outcome. Telegram may have accepted the message even if MongoDB has not recorded it. Such records are never retried automatically. Delivery is not exactly once. Inspect Telegram and `news_items` before changing a `SENDING` or `AMBIGUOUS` record to `SENT` or `FAILED` manually. Never reset a record without deciding whether a duplicate is acceptable.

A MongoDB document in `batch_locks` prevents overlapping executions. A crash can leave the lock behind; after verifying that no batch is still active and reviewing all `SENDING` records, remove only the `digest` lock as described in [deployment and recovery](doc/runbooks/deployment.md). Do not delete MongoDB volumes or databases as routine cleanup. A nonzero exit means startup, retrieval, analysis, database or delivery failed, or an unresolved delivery remains.

See [project state](doc/project-state.md), [acceptance matrix](doc/acceptance-matrix.md), and [verification report](doc/verification-report.md) for current evidence and limitations.
