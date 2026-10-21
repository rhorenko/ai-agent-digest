# ADR 0002 - Diverse source coverage and canonical slash verification

Accepted 2026-10-22 for the user's request for 20 sources, a monthly window and two output items.

The live log showed eight recent items but zero verified candidates. Public probes confirmed GitHub changelog URLs omit a trailing slash and return 301 to the same URL with a slash. Atom release feeds with only updated timestamps cannot meet the publication-date rule.

Keep strict publication dates and HEAD verification. Permit exactly one 301/308 where Location resolves to the exact original URL with a trailing slash added, preserving scheme, authority and query. Require the second HEAD to return 2xx. Retain the original URL and candidate ID for provenance and existing deduplication; do not migrate stored URLs. Explicitly log both statuses. Reject cross-origin, scheme/query/path changes, credentials, chains, temporary redirects, HEAD rejection and transport failures. This is a narrow canonicalization rule, not a general redirect policy or GET fallback.

Fetch all configured feeds, then check links round-robin newest-first within each source. A failed article is isolated; duplicate URLs do not consume additional unique-candidate budget. Defaults: 20 curated feeds, 30 days, two items, 60 unique candidate checks; at most 30 configured feeds. Existing context/output limits remain. Canonical verification adds at most one HEAD per checked URL. Feed order still breaks ties and a character limit may truncate the model context. Rolling 30 days is not a full archive crawl or a calendar month. Empty selections remain valid.

Read-only public verification on 2026-10-22 using application code found 60 candidates from 20 sources; all 20 remained represented after context bounding. No Claude/Telegram calls. See the verification report for exact evidence and limitations.
