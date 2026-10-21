# Pipeline logging

Scope: user request, FEAT-001-AC-08/09. Preserve processing and exit semantics.

1. INFO logs for startup mode, database initialization/lock/recovery, retrieval/deduplication, analysis/validation/persistence and delivery; elapsed time and counts.
2. Explicit reasons for empty runs; WARN/ERROR for source failures, retries, busy locks and failed stages. No credentials, full URLs, payloads, exception messages or stacks.
3. Capture logs for empty/deduplicated/zero-selection, recovery, failure and successful delivery; run focused tests then Maven verify and an offline package launch where available.
4. Sequential QA review, runbook, traceability and actual verification evidence.

Completed 2026-10-22: 47 tests passed, no failures/errors/skips; offline JAR first run/rerun verified. Sequential QA and limitations recorded in [verification report](../verification-report.md).
