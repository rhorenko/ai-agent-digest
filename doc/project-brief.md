# Project brief

## Problem and audience
A senior Java developer needs to identify consequential developments in agentic software engineering without reviewing promotional and repetitive news manually.

## Value and MVP
A one-shot batch selects up to five useful, recent, sourced items and delivers individual English Telegram messages. Persist analysis and delivery state so reruns avoid already delivered items and recover safely. An empty selection is valid. Profile, window and limit are configurable.

## Boundaries and constraints
Java 25, Spring Boot 4.0.5, Maven and the existing single-module one-shot execution. No web UI, scheduler, queue, paid API calls or real Telegram sends during verification. Source material is untrusted; only retrieved URLs and dates may be used. Delivery cannot be exactly once.

## Success signals
Verifiable sourced selection, durable delivery state, independent requirement-based checks, a reproducible offline execution, and an observable process exit status.

## Assumptions
RSS/Atom metadata provides limited evidence, not full article analysis. Unknown publication dates are excluded from the 24-hour selection. Timeouts on Telegram delivery are ambiguous and require manual review.
