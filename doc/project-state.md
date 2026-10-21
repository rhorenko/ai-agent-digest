# Project state

Updated 2026-10-22. Product: Agentic News Digest. Stage: source expansion/monthly settings implemented; sequential dev/qa verified, no subagents.

Feature: [FEAT-001](features/feat-001-news-digest.md). [Plan](plans/source-expansion-plan.md). [ADR 0002](adr/0002-source-coverage.md). [Evidence](verification-report.md).

Defaults now 20 feeds, rolling 30 days, at most two selected/delivered items, 60 unique candidate link checks; up to 30 feeds configurable. Candidate checks are round-robin, individual failures isolated. HEAD allows only one permanent same-URL trailing-slash redirect; other redirects rejected. Strict publication dates retained; updated-only release feeds removed from defaults. INFO logs now include article HEAD/rejection statuses. English-only/model compatibility/logging changes retained.

Checks: final Maven verify 55 passed, zero failures/errors/skips (real Mongo Testcontainers/HTTP stubs). Production Java public retrieval: 20 successful feeds, 60 candidates from 20 sources; bounded model context 54 candidates/20 sources/39974 chars. No Claude analyze or Telegram calls. Offline JAR first run delivered=1, rerun delivered=0, both exit 0; startup confirms feeds=20/window=PT720H/maxItems=2. Compose syntax valid; app image not rebuilt for current changes.

Existing data retained. Only agent_digest_sources_20261022_offline added for this check on loopback Mongo 27019. Old environment/IDE/external YAML settings override defaults; local profile does not import .env. Feed coverage is finite and 30 days is not an archive crawl. Two is a maximum. Model English/relevance and live delivery remain unverified; stale locks/UNKNOWN need manual reconciliation.

Next action: restart local launch with updated classes; remove old DIGEST_FEEDS override or replace its list, set DIGEST_WINDOW=30d and DIGEST_MAX_ITEMS=2. For containers rebuild the app image first.
