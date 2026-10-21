# Agentic News Digest

One-shot Java 25 / Spring Boot 4.0.5: verified RSS/Atom candidates в†’ one structured Claude selection в†’ MongoDB в†’ individual English Telegram messages. Defaults: 20 feeds, a rolling 30-day window, up to two useful items; empty selection sends nothing. Existing package, coordinates, roles and scripts are retained.

No spring-claude dependency was present. [ADR 0001](doc/adr/0001-bounded-feed-pipeline.md) records the documented Messages HTTP integration.

## Verify and run offline

Requires JDK 25 and Docker (including real Mongo Testcontainers).

~~~powershell
.\mvnw.cmd verify
.\scripts\build-container.ps1
docker compose -f docker/compose.yaml up -d --wait mongo
.\scripts\run-container.ps1 --digest.offline=true --digest.database=agent_digest_offline
~~~

Repeat the last command: delivered=0, no duplicate. Offline mode uses synthetic feed/model fixtures, the same validator/storage/processor, real MongoDB and console delivery. No credentials or external API calls. Offline databases must end in _offline or _test. Tests use local HTTP stubs and disposable Mongo databases. Image packaging skips tests; run host verify first.

## Local JAR

~~~powershell
java -jar target/agent-digest-0.0.1-SNAPSHOT.jar --digest.offline=true --digest.mongo-uri=mongodb://localhost:27019 --digest.database=agent_digest_offline
~~~

Mongo host port defaults to 27019, loopback only; Compose containers use mongo:27017. Keep demo and live databases separate.

## Live configuration

Copy .env.example to local .env. Set ANTHROPIC_API_KEY, CLAUDE_MODEL (recommended claude-opus-5-5; claude-sonnet-5-5 is cheaper), TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID, MONGODB_DATABASE=agent_digest and DIGEST_OFFLINE=false. Container scripts use root .env automatically. Run scripts/run-container.ps1 intentionally when you want paid analysis and real delivery.

Java does not load dotenv files automatically: export environment variables or use an untracked external YAML. MONGODB_URI configures the JAR; Compose uses its internal Mongo service. Profile, feeds, window, counts, token/context/time limits and retry/delivery settings are external; see [.env.example](.env.example). Never put secrets in command arguments or Git.

## News window, sources and count

Defaults live in [application.yaml](src/main/resources/application.yaml); [.env.example](.env.example) and Compose use the same values. IDE/JAR launches accept environment variables or program arguments:

~~~text
--digest.window=30d --digest.max-items=2 --digest.max-candidates=60
~~~

`DIGEST_WINDOW=30d` is the previous 30 days, not the previous calendar month. `DIGEST_MAX_ITEMS=2` caps both the new selection and total deliveries per run, including stored pending items; it does not guarantee two suitable news items. `DIGEST_MAX_CANDIDATES=60` bounds unique article link checks; the separate 40000-character context cap can reduce what Claude sees. `DIGEST_FEEDS` is a comma-separated list of HTTPS feeds (up to 30).

Existing environment variables, external YAML or IDE arguments override repository defaults. Remove an old `DIGEST_FEEDS` override to use the new 20-source list, or replace it with the list in `.env.example`. A Spring `local` profile alone does not import `.env`. See the [configuration table and source list](doc/runbooks/local-development.md).

## Recovery and limits

--digest.recovery-only=true skips feeds/Claude and retries stored safe pending items. Exit codes: 0 success/empty, 1 operational/config failure, 2 unresolved delivery, 3 overlapping batch. UNKNOWN never retries automatically. Exactly-once is not promised; see [operations](doc/runbooks/deployment.md) before reconciliation or removing a stale lock.

Default coverage: 20 feeds spanning AI/agents and Java development, including GitHub, OpenAI, Hugging Face, Google Research, Microsoft, JetBrains, Inside Java, InfoQ and Ollama. Only recent dated entries with verified original links qualify. Analysis uses feed excerpts/headlines, never claimed as full-text. See [source coverage](doc/runbooks/local-development.md).

English is the only supported delivery language. Claude selects English titles/source material and writes English analysis. Java rejects non-Latin letters in titles/analysis, including stored recovery items; Latin-script language identification still depends on the model. Existing stored content is not translated automatically.

[Feature](doc/features/feat-001-news-digest.md) В· [Architecture](doc/architecture.md) В· [Verification](doc/verification-report.md) В· [State](doc/project-state.md)
