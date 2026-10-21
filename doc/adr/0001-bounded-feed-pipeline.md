# ADR 0001 — Bounded feed analysis and durable delivery

Accepted 2026-10-21.

Inspection of pom.xml, sources, configuration and Maven cache found **no spring-claude dependency**: no coordinates/version, configured client, library APIs or search tools exist to reuse. Do not guess a similarly named library. Use JDK HttpClient with the documented Claude Messages HTTP contract and output_config.format JSON schema. A normal Messages request has no automatic current-news access; use configurable bounded RSS/Atom feeds and verify their article links.

Official contracts checked: [Messages](https://platform.claude.com/docs/en/api/http/messages), [structured output](https://platform.claude.com/docs/en/build-with-claude/structured-outputs), [errors](https://platform.claude.com/docs/en/api/errors), [Telegram](https://core.telegram.org/bots/api#sendmessage). No live Claude call made.

No existing Mongo access approach: use synchronous Mongo driver and news/runs/locks collections. Insert-only analysis and normalized URL uniqueness preserve delivery history. Singleton database mutex never expires automatically, avoiding concurrent sends after a paused process resumes. Release in finally; manual crash unlock after confirming the process stopped.

Persist SENDING before Telegram I/O. Stranded SENDING becomes UNKNOWN under the next exclusive lock; UNKNOWN never retries automatically. A crash after Telegram accepts but before Mongo records success remains ambiguous. Exactly-once is not promised.

Plain text avoids formatting injection. Retry only explicit Telegram 429 rejections in an invocation; transport errors/5xx/malformed success are UNKNOWN. Safe RETRY items can run next batch. Claude only retries explicit transient 429/5xx with a finite budget; invalid output or transport timeout does not cause another paid call. Recovery-only bypasses sources/Claude.

Offline fixtures enter the same validation/persistence/processor with real Mongo and console delivery. Use a separate offline database. Tradeoffs: bounded feed/excerpt coverage, conservative uncertainty, manual crash recovery.
