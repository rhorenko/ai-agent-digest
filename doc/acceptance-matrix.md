# Acceptance matrix

| Criterion | Evidence | Status |
|---|---|---|
| FEAT-001-AC-01 | FeedParserTest covers RSS/Atom, stale and undated entries; DigestBatchTest covers an empty selection. GitHub feed and one article returned HTTP 200. Default coverage is one feed; live relevance quality not tested. | Partial live verification |
| FEAT-001-AC-02 | AnalysisValidatorTest rejects invented IDs, duplicates, missing fields and score/date boundaries. ExternalClientsTest verifies schema request and valid/invalid stub responses. Live Claude/model quality not tested. | Partial live verification |
| FEAT-001-AC-03 | MongoDigestStoreTest uses MongoDB Testcontainers for insert, retrieval, unique URL, state changes and retry timing. Offline container persisted a record and unique index. | Pass offline |
| FEAT-001-AC-04 | HTTP stubs exercise success, rate limit, malformed response, bounded text and server error; DigestBatchTest exercises ordering and partial failures. Real Telegram delivery not authorized. | Partial live verification |
| FEAT-001-AC-05 | DigestBatchTest checks no duplicate sends, partial failure, reprocessing, ambiguity and mixed ordering; MongoDigestStoreTest checks lock, deduplication and deferred retries. Offline container rerun emitted no message. | Pass offline |
| FEAT-001-AC-06 | Offline JAR and container exit 0, missing credentials exit 1; configuration and secret handling documented. | Pass offline |
| FEAT-001-AC-07 | `mvnw verify`: 20 tests including MongoDB Testcontainers; JAR, Docker build, database, offline run, rerun and record inspection executed. Live external integrations deliberately untested. | Pass offline; live unverified |
