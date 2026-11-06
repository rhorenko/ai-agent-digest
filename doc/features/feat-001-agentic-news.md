# FEAT-001 — Agentic news digest

Status: Implemented, offline verified; live provider verification pending. Priority: highest. The batch serves the profile in the project brief. No scheduler, UI, or REST API is included.

- `FEAT-001-AC-01`: Discover at most the configured number of recent qualifying items from configurable sources; exclude irrelevant and undated material, and permit an empty selection.
- `FEAT-001-AC-02`: Analyze retrieved, bounded source data with Claude using externally configured model, prompt and profile; validate structured output and reject invented source references or missing fields.
- `FEAT-001-AC-03`: Persist source and analysis data with normalized unique URLs, timestamps and delivery results before sending; retain errors and message IDs.
- `FEAT-001-AC-04`: Deliver one English message per selected item in descending score order, with bounded size and source link; persist partial failures and honor rate limits.
- `FEAT-001-AC-05`: Reruns do not resend successes; retry definitively undelivered items without analysis; ambiguous outcomes require explicit recovery. Prevent overlapping batches.
- `FEAT-001-AC-06`: Exit with an accurate status; validate configuration and keep secrets outside source, logs and images.
- `FEAT-001-AC-07`: Verify success, empty/irrelevant/invalid/missing data, deduplication, persistence failures, reruns, delivery, rate limits, partial failure and termination using offline tests; run package and Docker checks when available.

Source metadata is not full-text analysis. Publication date is required for selection. Claude and Telegram are never contacted with real credentials in tests.

Plan: [implementation plan](../plans/feat-001-plan.md). Evidence: [matrix](../acceptance-matrix.md), [verification report](../verification-report.md).

Acceptance: Offline checks passed; live Claude quality and Telegram delivery are unverified. See the acceptance matrix and verification report.

