# Source expansion and monthly digest

User request 2026-10-22: around 20 sources, previous month (rolling 30 days), at most two selected/delivered items per run; explain configuration and empty-run logs. FEAT-001-AC-01/02/05/08/09.

1. Verify candidate RSS/Atom endpoints and publication timestamps; configure 20 feeds, 30d, two items, and 60 link checks. Keep context/output bounds. Align application/Compose/env examples.
2. Retrieve feeds first, then verify candidates round-robin so early feeds cannot consume all checks. Keep newest-first order within each feed and dedup globally; isolate article failures.
3. Permit one explicitly logged same-origin trailing-slash-only canonical redirect; retain original provenance/URL. Reject other redirects, credentials and non-2xx. Expose rejected HEAD statuses at INFO. No GET fallback or arbitrary redirects.
4. Unit/HTTP tests for feed count validation, month boundaries, diverse retrieval, URL rejection/isolation and two-item cap. Sequential QA, full verify, offline JAR run, public read-only source probe without Claude/Telegram/Mongo mutations.
5. Update runbook, requirements, traceability, evidence and compact state.

Completed: 55 tests pass without skips, public production-code probe returns 60 candidates from 20 feeds, offline JAR first/rerun and Compose syntax verified. No paid Claude/Telegram calls or current app image build. Evidence and limits in the verification report.
