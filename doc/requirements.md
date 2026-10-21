# Requirements

Source: user request, 2026-10-21. Delivery: [FEAT-001](features/feat-001-news-digest.md).

| ID | Requirement |
|---|---|
| REQ-001 | Retrieve attributable recent sources (20 default feeds, rolling 30 days); distribute candidate checks across sources and select at most two items with one bounded Claude request. |
| REQ-002 | Validate structured analysis against retrieved candidates and retain source limitations. |
| REQ-003 | Persist before delivery; normalize/deduplicate URLs, serialize batches and recover safe undelivered items. |
| REQ-004 | Send at most two individual English Telegram messages per run by default, in score order; retain success, rejection and ambiguous outcomes. |
| REQ-005 | Typed external configuration, secret safety and explicit one-shot exit status. |
| REQ-006 | Unit/HTTP/Mongo tests plus real offline JAR and Docker verification. |

Article content is untrusted. Never invent dates, URLs, articles or full-text access. No live paid calls or real messages are authorized.
