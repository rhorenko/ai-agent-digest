# Project state

Last updated: 2026-10-29

## Stage and goal
FEAT-001 implemented and verified offline; live integration remains unverified by user instruction.

## Current artifacts
[Feature](features/feat-001-agentic-news.md), [plan](plans/feat-001-plan.md), [architecture](architecture.md), [ADR](adr/0001-source-bound-analysis.md), [acceptance matrix](acceptance-matrix.md), [verification report](verification-report.md), [README](../README.md).

## Completed
One-shot configurable RSS/Atom, Claude structured analysis, MongoDB persistence and safe recovery, Telegram delivery, fixtures and Compose. Maven verify passed 20 tests including MongoDB Testcontainers. The final executable JAR and container completed offline against separate MongoDB databases; a container rerun did not resend. The verification volumes were retained.

## Limitations
No existing `spring-claude` client was present; the documented Anthropic Messages HTTP API is used. No live paid Claude call or real Telegram send has been authorized or performed. Default coverage is the GitHub AI/ML feed, and source material is metadata rather than full articles. Delivery can be ambiguous and is not exactly once. A crash may require manual lock and delivery-state review.

## Exact next action
For a live run, the owner configures credentials and chat ID locally per README, selects additional trusted feeds if needed, and runs the batch. Validate live provider compatibility and relevance only when a paid call and real message are explicitly authorized.
