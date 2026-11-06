# Verification report

## 2026-10-29 — FEAT-001 QA

Environment: Windows, Temurin JDK 25.0.3, Maven Wrapper, Docker Desktop 29.2.1 Linux engine, MongoDB 8.0. No paid Claude call and no real Telegram message was made.

- `.\mvnw.cmd --batch-mode --no-transfer-progress verify`: PASS; 20 tests, 0 failures, errors or skips. MongoDB Testcontainers exercised persistence, retrieval, unique index, concurrent lock, retry timing and delivery states. HTTP stubs exercised Claude schema/response, Telegram success, 429, malformed/server failures, oversized response and request deadline. The DOCTYPE rejection test intentionally emits an XML parser diagnostic.
- Executable JAR offline against a separate Docker MongoDB database: PASS, fixture message printed, exit 0. The JAR without live credentials failed validation with exit 1.
- `docker compose -p agent-digest-verify -f docker/compose.yaml config --quiet`: PASS.
- `docker compose -p agent-digest-verify -f docker/compose.yaml build --no-cache app`: PASS. One preceding cached build failed because Docker reported a missing parent snapshot; no cache or volume deletion was performed.
- `docker compose -p agent-digest-verify -f docker/compose.yaml run --rm -e DIGEST_OFFLINE=true -e MONGODB_DATABASE=agent_digest_verify_final2 app`: PASS, MongoDB healthy, fixture message, exit 0. The same command again: PASS, no duplicate message, exit 0.
- `mongosh` inspection of `agent_digest_verify_final2`: one item, status SENT, messageId `offline`, score 8, processed timestamp, unique `url_1` index and zero locks. Data remains in the verification volume.
- Public GitHub AI/ML RSS feed: HTTP 200; sample original article HEAD: HTTP 200.

QA reviewed the criteria independently of the development checks and added mixed recovered/new ordering, discovery failure, bounded response, timeout and valid schema-shaped HTTP response tests. Fixes were retested. Live Claude schema/model compatibility, feed coverage over time, Telegram delivery, real rate-limit behavior and model relevance/faithfulness remain unverified; external HTTP stubs cannot prove provider behavior. No claim of exactly-once delivery is made. The default single feed does not comprehensively cover the requested topics; it is configurable.
