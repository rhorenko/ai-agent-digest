# Verification report

Date: 2026-10-21. Target: Agentic News Digest FEAT-001, working-tree implementation. Sequential roles: ceo РІвЂ вЂ™ po РІвЂ вЂ™ architect РІвЂ вЂ™ dev РІвЂ вЂ™ qa, with dev fixes returned for QA regression. No subagents used.

Environment: Windows, host Temurin 25.0.3, Spring Boot 4.0.5, Maven Wrapper; Docker Desktop 4.64.0 / Engine 29.2.1. Runtime image Temurin JRE 25.0.2, Mongo 8.0.16. Actual resolved dependencies: mongodb-driver-sync 5.6.4, jackson-databind 2.21.2, testcontainers-mongodb 2.0.4. No existing spring-claude artifact/client was present (pom, sources, configuration, cache inspected).

## Executed checks

| Check / command | Actual result |
|---|---|
| java -version; docker version | JDK 25 and Docker daemon available |
| Initial developer mvnw.cmd test | 25 passed, no skips |
| QA-focused mvnw.cmd -Dtest=QaBoundaryTests test | Initially two failures; see findings below |
| Final .\\mvnw.cmd verify | PASS: 35 tests, 0 failures/errors/skips; executable JAR repackaged |
| Final .\\mvnw.cmd clean verify and clean report aggregation | PASS: 35 tests, 0 failures/errors/skips; removed pre-existing stale target reports; rebuilt JAR rerun exit 0 |
| mvnw.cmd dependency:tree with relevant includes | PASS; versions above |
| Public curl GET: GitHub changelog, OpenAI news, Claude Code releases | All HTTP 200; no paid calls |
| Presumed Anthropic news/rss.xml | HTTP 404; excluded from defaults |
| docker compose -f docker/compose.yaml build | PASS; agent-digest:local, final image rebuilt after final application change |
| docker compose -f docker/compose.yaml up -d --wait mongo | PASS; new agent-digest Mongo healthy, persistent agent-digest_mongo-data volume retained |
| java -jar target/agent-digest-0.0.1-SNAPSHOT.jar --digest.offline=true --digest.mongo-uri=mongodb://localhost:27019 --digest.database=agent_digest_qa_20261021_offline | PASS: first delivered=1, exit 0; repeat delivered=0, exit 0 |
| Final packaged JAR rerun against same offline database | PASS: delivered=0, unresolved=0, exit 0 |
| Compose run --rm with DIGEST_OFFLINE=true and MONGODB_DATABASE=agent_digest_final_20261021_offline | PASS: first delivered=1, exit 0; rerun delivered=0, exit 0 |
| mongosh query final container database | news=1, delivered=1, successful runs=2, locks=0 |
| mongosh query separate JAR database after two runs | news=1, delivered=1, successful runs=2, locks=0 |
| Container with a preseeded test lock | BUSY, exit 3; only that known test lock was removed |
| Recovery-only container with preseeded UNKNOWN test record | PARTIAL, delivered=0, unresolved=1, exit 2 |
| Container with invalid offline DB suffix | Configuration rejected, exit 1 |
| Runtime image inspection / command -v javac | User app; javac absent |
| run-container.ps1 invoked from outside repository | PASS, proper root resolution; offline rerun exit 0 |
| git diff --check | PASS |

Initial container database agent_digest_container_qa_20261021_offline also verified the same successful two-run invariant. Separate agent_digest_exit_qa_20261021_offline retains the deliberately unresolved fixture used for exit checks. No existing user database/container was modified; no Mongo volumes were deleted. Compose Mongo remains running on loopback port 27019.

## 2026-10-22 dev change: current-model compatibility

Current Claude models (Opus 5.5 / Sonnet 5.5 / Haiku 5.5) think by default, so responses carry thinking blocks before the structured text block; the former single-block check rejected them. ClaudeAnalyzer now ignores thinking/redacted_thinking blocks, still requires exactly one text block and rejects any other block type. Added CLAUDE_EFFORT (default low, blank omits it) and a separate CLAUDE_TIMEOUT (default 120s); CLAUDE_MAX_TOKENS default raised to 16000 because thinking shares the budget. Recommended model: claude-opus-5-5.

| Check / command | Actual result |
|---|---|
| .\\mvnw.cmd test -Dtest=HttpIntegrationTests,QaBoundaryTests | PASS |
| .\\mvnw.cmd verify | PASS: 37 tests, 0 failures/errors/skips; JAR repackaged |
| Offline JAR against agent_digest_offline (Compose Mongo started first) | PASS: status SUCCESS, exit 0; new properties bound and validated |

Not run: live Claude call with the recommended model (paid, not authorized); container image rebuild. QA has not yet independently reviewed this change.

## Independent QA findings and fixes

QA independently derived boundary cases from the feature before extending tests. QaBoundaryTests initially reproduced:
1. Lock release failure logged a warning but returned success. Dev changed it to exit 1/LOCK_RELEASE_FAILED and attempts to update the run audit.
2. Context too small for any candidate silently returned empty. Dev added explicit failure. Final implementation counts serialized user input plus system prompt/schema and validates again before calling Claude.

Both regression tests pass. QA also added slow-body timeout (not just header timeout), oversized response rejection, failed SENDING-write/no-send, invalid configuration, bounded model 429 retry, longest valid Telegram message, actual feed-link verification and the Telegram-success/Mongo-write gap. Gap test proves subsequent recovery changes SENDING to UNKNOWN without sending again.

Final totals: AgentDigestApplicationTests 2; AnalysisValidatorTests 5; BatchProcessorTests 6; FeedSourceTests 3; HttpIntegrationTests 6; MongoDigestStoreTests 5; QaBoundaryTests 8 = 35.

## Limitations and unrun checks

- No paid/live Claude requests and no real Telegram messages: user did not authorize them. Model availability, API credentials, real relevance/factual quality, English fluency and destination permissions need an intentional live trial.
- Feed GET accessibility was checked, but no claim of exhaustive or complete 24-hour source coverage. HEAD/redirect/date restrictions can omit otherwise useful material.
- Contract tests simulate current official HTTP documentation; no pre-existing spring-claude APIs were invented.
- Offline console message IDs are synthetic. The same processor/validation/storage runs, but console output does not establish real Telegram delivery.
- A crash lock requires manual confirmation of stopped ownership. UNKNOWN needs reconciliation. Exactly-once delivery is not provided.
- Default Compose Mongo is local-only and unauthenticated; production hardening/backup was outside this local execution task.
- Mockito emitted its standard Java-agent warning; tests passed. Image packaging skips tests intentionally, with full host verification executed separately.
- One initial PowerShell mongosh query lost nested quotes and failed; corrected regex-based queries above executed successfully.

Assessment: implementation accepted against automated/offline criteria with the explicit live-integration limitations above. See [matrix](acceptance-matrix.md) and [operations](runbooks/deployment.md).

## 2026-10-22 English-only change: sequential dev/qa verification

Scope: user explicitly requested English-only code, logic and news delivery, superseding the original language requirements. FEAT-001-AC-02/03/06 and current documentation updated. Prompts, formatter labels/notices, offline feed/model fixtures and test data are English. All application/test source files scanned for literal Cyrillic: none remain; negative script cases use Unicode escapes.

QA scenarios derived from the English-only requirement: valid English and punctuation/Latin proper names; unsupported/mixed scripts and numeric-only text in each of the four analysis fields; unsupported source title; recovery of older unsupported title/analysis with zero HTTP requests; both material limitation notices; longest permitted message; actual English selection instructions in the outbound Claude request. Sequential review performed in this session, without subagents. No additional defects found.

| Check / command | Actual result |
|---|---|
| .\mvnw.cmd "-Dtest=AnalysisValidatorTests,HttpIntegrationTests,QaBoundaryTests" test | PASS: 24 tests, zero failures/errors/skips |
| Final .\mvnw.cmd verify after QA assertions | PASS: 40 tests, zero failures/errors/skips; executable JAR packaged |
| java -jar target/agent-digest-0.0.1-SNAPSHOT.jar --digest.offline=true --digest.mongo-uri=mongodb://localhost:27019 --digest.database=agent_digest_english_20261022_offline | PASS: English fixture, delivered=1, unresolved=0, exit 0 |
| docker compose -f docker/compose.yaml build app | PASS: agent-digest:local rebuilt with English resources and current-model compatibility |
| docker compose -f docker/compose.yaml run --rm --no-deps app --digest.offline=true --digest.database=agent_digest_english_container_20261022_offline | PASS: English fixture, delivered=1, unresolved=0, exit 0; repeat delivered=0, exit 0 |

Limitations: no paid/live Claude or real Telegram requests. Script validation distinguishes Latin from unsupported scripts, not English from other Latin-script languages; English semantics/source selection require model adherence and remain unverified live. Old records are retained and not translated; unsupported stored delivery content is REJECTED / TELEGRAM_INVALID_MESSAGE. Tests use escaped unsupported characters intentionally. Container application sources were unchanged after build; final QA edits added test assertions only. Existing databases/volumes preserved; only isolated offline fixture databases added.

## 2026-10-22 Pipeline logging: sequential dev/qa verification

Scope: user's request to explain a live `SUCCESS delivered=0`; FEAT-001-AC-08/09, [plan](plans/logging-plan.md). INFO stage/count/request/status/duration logs and explicit empty-run reasons added without changing processing/deduplication/retry/exit semantics. Failures identify the batch stage and exception type; Telegram failure categories are locally generated. Credentials, URLs, payloads and exception messages/stacks are omitted from the new logs.

Sequential QA review checked empty sources, all URLs already stored, zero analysis selection, recovery-only delivery, busy lock, failure stage and credential-bearing exceptions. Additional HTTP-stub assertions check actual feed/Claude/Telegram log events and omission of credentials, chat ID, source text and payloads. No subagents. Existing database-ordering, delivery-state and lock regression checks remain intact; no outstanding defects found in the tested scope.

| Check / command | Actual result |
|---|---|
| `.\mvnw.cmd '-Dtest=BatchLoggingTests,BatchProcessorTests,HttpIntegrationTests' test` | PASS: 21 tests before the additional HTTP logging assertion |
| Final `.\mvnw.cmd verify` | PASS: 47 tests, zero failures/errors/skips, real Mongo Testcontainers; executable JAR packaged |
| Offline JAR first run, command below | PASS: delivered=1, unresolved=0, exit 0; stage/count/persistence/delivery/lock logs visible |
| Same offline JAR command repeated | PASS: delivered=0, exit 0; retrieved=1/alreadyStored=1/fresh=0; ALL_ALREADY_STORED and NO_PENDING_ITEMS visible |
| `git diff --check` | PASS |

Offline command (PowerShell):

~~~powershell
java -jar target/agent-digest-0.0.1-SNAPSHOT.jar --digest.offline=true --digest.recovery-only=false '--digest.mongo-uri=mongodb://localhost:27019/?retryWrites=true&w=majority' --digest.database=agent_digest_logging_20261022_2325_offline
~~~

Local evidence: `target/logging-verify.log`, `target/logging-offline-first.log`, `target/logging-offline-rerun.log` (generated, not committed). Only the isolated offline database was added; existing data retained. Offline fixture message output is unchanged and still includes its synthetic content. No live Claude or Telegram calls made; the exact cause of the user's earlier empty run cannot be inferred retrospectively. Docker application image was not rebuilt for this logging-only change; run the rebuilt JAR or rebuild the image to use new logs.

## 2026-10-22 Source expansion and monthly window: sequential dev/qa

Scope: user requested about 20 sources, news for the last month and at most two output items; [plan](plans/source-expansion-plan.md), [ADR 0002](adr/0002-source-coverage.md). Defaults now 20 feeds / rolling 30 days / two selected and delivered items / 60 unique link checks. Validated source limit expanded to 30. Round-robin candidate checking prevents early feeds consuming the entire budget; per-article errors are isolated. Only one permanent same-URL trailing-slash redirect may be verified, retaining original provenance. All other redirect restrictions remain.

Original live log interpretation: all three feed documents loaded/parsed; GitHub/OpenAI contributed four date-qualified entries each but no verified candidates, and Claude Code had zero eligible entries. No model request was made. Read-only probes reproduced GitHub's missing-trailing-slash redirect and confirmed Claude Code Atom entries have only updated timestamps, which the publication rule intentionally excludes. The original OpenAI article rejection status was not present in the supplied INFO log, so no specific status is attributed retrospectively.

Sequential QA scenarios derived from the request: actual application YAML binding and overrides, 20/30 accepted feeds and 31/zero rejected, exact rolling-month boundary/older/future/undated entries, source diversity under a tight budget, duplicate budget accounting, isolated malformed redirects, allowed permanent canonical slash and rejection of cross-origin/scheme/credentials/query/path changes, temporary redirects, chains and non-2xx destinations. Two-item selection rejects a third response item; real Mongo recovery sends the two highest scored pending items and leaves a third pending. Existing English validation, persistence, lock, secret-safe logging and HTTP boundaries remain covered. An initial focused run caught the old ten-feed validation limit; corrected and all relevant checks rerun. No subagents; no outstanding defects in tested scope.

| Check | Actual result |
|---|---|
| `.\mvnw.cmd '-Dtest=SourceExpansionTests,FeedSourceTests,HttpIntegrationTests' test` | PASS: 19 tests before adding the extra selection/delivery-cap QA scenarios |
| Final `.\mvnw.cmd verify` | PASS: 55 tests, zero failures/errors/skips; real Mongo Testcontainers; executable JAR packaged |
| Java public-source probe using production FeedSource/HttpTransport and configured default feeds, 10s request timeout, UTC clock | PASS: successfulFeeds=20 failedFeeds=0 checkedLinks=60 candidates=60, from 20 sources; no paid/model/delivery/database calls |
| Production ClaudeAnalyzer.boundContext on public candidates (no analyze call) | PASS: 54 candidates / 20 sources, 39974 context characters <=40000 |
| Packaged offline JAR first run | PASS: feeds=20 window=PT720H maxCandidates=60 maxItems=2; queue limit=2; delivered=1, unresolved=0, exit 0 |
| Same offline JAR command repeated | PASS: ALL_ALREADY_STORED, pending=0 limit=2, delivered=0, exit 0 |
| `docker compose -f docker/compose.yaml config --quiet` | PASS, without printing populated configuration |
| `git diff --check` | PASS |

Offline command:

~~~powershell
java -jar target/agent-digest-0.0.1-SNAPSHOT.jar --digest.offline=true --digest.recovery-only=false '--digest.mongo-uri=mongodb://localhost:27019/?retryWrites=true&w=majority' --digest.database=agent_digest_sources_20261022_offline
~~~

Generated local evidence (not committed): target/source-probe.json, target/source-probe-additional.json, target/SourceProbe.java, target/source-expansion-public.log, target/source-expansion-focused.log, target/source-expansion-verify.log, target/source-expansion-offline-first.log and target/source-expansion-offline-rerun.log. SourceProbe loads shipped YAML with MockEnvironment, sets offline/test database and a 10-second HTTP timeout, then explicitly invokes only FeedSource.retrieve and ClaudeAnalyzer.boundContext; it never launches the batch or calls analyze/send. It was compiled with target/classes plus the Maven dependency classpath and run as com.agents.digest.SourceProbe.

Limitations: public availability is point in time; Python and Java probes received different access statuses for some hosts. Feeds expose finite recent entries, not a guaranteed complete monthly archive. Thirty days is a rolling duration, not a calendar month. Two is a maximum, not a guaranteed count or minimum relevance. Context bounds may reduce candidates. No live paid Claude or actual Telegram calls; model choice/quality and live delivery remain unverified. Docker app image was not rebuilt/run for this change; Compose syntax checked and packaged JAR exercised. Only the isolated agent_digest_sources_20261022_offline database was added; existing databases retained. Existing environment/IDE/external YAML settings may override the new defaults and must be updated/removed locally.
