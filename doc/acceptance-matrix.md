# Acceptance matrix

English-only criteria rechecked 2026-10-22; original broader verification 2026-10-21 against FEAT-001 and the user's original requirements. Evidence: [report](verification-report.md). Automated paths use fixtures/stubs; live model judgment and real Telegram are explicitly unverified.

| Criterion | Checks and evidence | Result |
|---|---|---|
| FEAT-001-AC-01 | SourceExpansionTests shipped defaults/overrides/source-count bounds/month boundary/round-robin/duplicate and redirect isolation; production public probe 20 feeds/60 candidates/20 sources; FeedSourceTests (RSS/Atom, dates/window/missing dates, XXE); HttpIntegrationTests.sourceRetrievalVerifiesOriginalLinkAndExcludesFailedVerification; QA response bound; public feed HTTP 200 checks | PASS for retrieval contract; limited documented coverage |
| FEAT-001-AC-02 | SourceExpansionTests two-item selection maximum; public context bound 54 candidates/20 sources; Claude structured HTTP request test, empty/irrelevant fixture selection, versioned English prompt and English source-selection instructions asserted in HTTP request; context/output limits | PASS contract; real model relevance/quality UNVERIFIED |
| FEAT-001-AC-03 | AnalysisValidatorTests malformed/missing/extra/duplicate/unknown/score checks; English prose accepted; each prose field rejects unsupported/mixed scripts and numeric-only text; selected source title checked; Claude truncation; BatchProcessorTests invalid selection prevents save/send | PASS |
| FEAT-001-AC-04 | MongoDigestStoreTests real persistence/roundtrip/sort/unique index/message ID/run audit; mongosh record queries | PASS |
| FEAT-001-AC-05 | Real Mongo two-item delivery cap leaves a third pending; rerun/recovery/lock tests; database save/SENDING-write failures block send; acceptance-to-persistence gap test; JAR/container reruns | PASS |
| FEAT-001-AC-06 | Formatter required sections/longest valid message; HTTP plain-text Telegram request; ordered processor/storage tests; English container console output; stored unsupported prose/title rejected without HTTP; headline/excerpt notices | PASS fixture/stub |
| FEAT-001-AC-07 | HTTP 429 retry budget/defer, rejection, malformed/5xx, slow-body timeout; Mongo interrupted/UNKNOWN/future retry; gap recovery | PASS stub/real storage |
| FEAT-001-AC-08 | Typed configuration tests, redacted toString, missing credentials fail before I/O, separate offline DB suffix, image build context/runtime inspection | PASS tested boundaries |
| FEAT-001-AC-09 | Application lifecycle failure; processor success/partial/busy/failure; BatchLoggingTests empty/dedup/zero-selection/recovery/failure/busy reasons; HTTP log status/duration/redaction assertions; offline JAR first/rerun stage logs and exit 0; prior container exits 0/1/2/3 | PASS automated/offline; live logs unverified |
| FEAT-001-AC-10 | Maven verify 40/0 failures/0 errors/0 skipped; Docker build, Mongo readiness, offline JAR/container/records/reruns | PASS |

Real paid Claude selection and actual Telegram acceptance/delivery are NOT verified. There is no claim that fixtures demonstrate live relevance, language quality, exhaustive source coverage or exactly-once semantics.

Logging update 2026-10-22: final Maven verify 47 tests, zero failures/errors/skips. New logging redaction assertions supplement AC-08; commands and limits are in the latest verification-report section.

Source expansion update 2026-10-22: final Maven verify 55 tests, zero failures/errors/skips; Java public retrieval 20 successful feeds/60 candidates, all 20 sources retained after context bounding. Offline JAR first run/rerun and Compose syntax verified; model/live delivery and updated app container are unverified.
