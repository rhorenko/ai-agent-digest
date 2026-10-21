# Architecture

One Maven module, Java 25, Spring Boot 4.0.5 and non-web ApplicationRunner/context closure remain.

| Component | Responsibility |
|---|---|
| DigestProperties | Typed bounded settings; conditional live credential validation |
| FeedSource | Secure RSS/Atom parsing, dates, original URL verification and excerpt provenance |
| ClaudeAnalyzer / AnalysisValidator | Structured Messages call and strict candidate correspondence |
| MongoDigestStore | Unique URL, batch mutex, run audit and durable delivery state |
| TelegramSender / MessageFormatter | Plain English messages, bounded 429 retries and conservative ambiguity |
| BatchProcessor | Recovery, retrieval, validated persistence, ordered delivery, exit status |
| Offline fixtures | Same validator/processor, real Mongo, console sink |

Flow: candidates в†’ Claude candidate IDs and English analysis в†’ validation в†’ Mongo PENDING в†’ SENDING в†’ DELIVERED/RETRY/REJECTED/UNKNOWN. All selected items persist before any sends. Existing URLs skip reanalysis. Recovery-only sends stored safe items without Claude. Model cannot author source metadata.

[ADR](adr/0001-bounded-feed-pipeline.md) defines actual integration inspection and failure guarantees. Mongo uses acknowledged majority writes and finite timeouts. Mutex scopes to one database; use one database per destination. Offline/live databases are separate. Runs retain processing timestamps, outcome, counts and safe error categories.

[ADR 0002](adr/0002-source-coverage.md): fetch feeds then verify candidates round-robin; permit only one permanent same-origin trailing-slash redirect while retaining original provenance. Default source/window/output limits are 20 feeds/30 days/two items.
