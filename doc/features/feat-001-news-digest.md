# FEAT-001 Р Р†Р вЂљРІР‚Сњ Agentic News Digest

Priority 1. Owner po. Requirements REQ-001 through REQ-006. Status: accepted for automated/offline scope; live trial unverified.

## Acceptance criteria

- FEAT-001-AC-01: Configurable RSS/Atom sources, 20 default sources, rolling 30-day window, bounded context/candidates and round-robin link checks across sources. Verify original URLs; allow only one same-origin permanent trailing-slash redirect to a 2xx response. Missing/future dates are conservatively excluded.
- FEAT-001-AC-02: External Claude model, versioned prompt/schema, zero to configured maximum (two by default) selected items, integer scores 1Р Р†Р вЂљРІР‚Сљ10, English summary/relevance/action/short score reason. Select English titles and source material only. Irrelevant material can yield empty.
- FEAT-001-AC-03: Invalid JSON, duplicate/unknown IDs, missing/extra fields, invalid scores and truncated/refused output fail closed before new persistence/delivery. Source metadata comes only from retrieved candidates. Reject unsupported scripts in selected titles and analysis; recheck titles/analysis at delivery, including recovery. English semantics depend on the model prompt.
- FEAT-001-AC-04: Mongo stores provenance, analysis, processing timestamp, delivery state, attempt time, error category and successful message ID; normalized URL unique index.
- FEAT-001-AC-05: Delivered items never resend on normal reruns. Recovery-only retries safe pending items without Claude. Persistence failure prevents sending. Overlapping batches cannot send concurrently.
- FEAT-001-AC-06: One item Р Р†РІР‚В РІР‚в„ў one plain English message, descending importance, title/score/summary/relevance/action/source/original URL/material limitation, within 4096 characters.
- FEAT-001-AC-07: Durable distinct states for timeouts, rejection, rate limiting, partial failure and interruption. Bound retries/request times; never automatically retry ambiguous deliveries.
- FEAT-001-AC-08: Typed validated configuration, secrets external, offline mode cannot call paid Claude or real Telegram.
- FEAT-001-AC-09: Close resources and report batch counts; log pipeline stages, counts, empty-run reasons, external HTTP attempts/status/durations and safe failure stages without credentials or payloads; exits 0 success/empty, 2 partial/unresolved delivery, 1 operational failure, 3 overlapping batch.
- FEAT-001-AC-10: Plain unit tests, HTTP stubs, real Mongo Testcontainers; verify Maven/package, offline JAR, Docker build/readiness/run, records and rerun where available.

No server/scheduler/new modules. Limited feed coverage and excerpt analysis are explicit. No exactly-once guarantee. Non-expiring crash lock and ambiguous delivery need manual reconciliation after stopping old processes. Separate offline/live databases.

[Plan](../plans/feat-001-plan.md), [ADR](../adr/0001-bounded-feed-pipeline.md), [matrix](../acceptance-matrix.md), [evidence](../verification-report.md).

Acceptance result, 2026-10-21: 35 automated checks passed with no skips; packaged JAR and Docker offline processing/rerun/records and container exit codes verified. QA findings fixed and regression checked. Paid Claude/model quality and actual Telegram delivery intentionally not exercised. See the matrix for criterion-level evidence and limits.

2026-10-22 source-expansion update: 20 feeds, rolling 30 days and maximum two items; round-robin verification and narrow canonical-slash redirects. Automated/public-retrieval/offline scope verified (55 tests, zero skips; 60 public candidates from 20 sources). Live Claude selection/Telegram unverified. [Plan](../plans/source-expansion-plan.md), [evidence](../verification-report.md).
