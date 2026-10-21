# Agentic News Digest

Help a Senior Java Developer building personal products and Telegram bots follow practical advances in agentic development without promotional noise. Prioritize substantial tools, engineering research, architecture, testing, independent verification, context/harness engineering and operating costs.

MVP: one externally triggered batch retrieves recent attributable sources, uses Claude to select/analyze/rank, persists in MongoDB and delivers separate English Telegram messages. Defaults: 20 feeds, previous 30 days and at most two items; empty is valid. Externalize profile and limits.

Preserve Java 25, Spring Boot 4.0.5, package, coordinates, one module, one-shot execution, Docker and scripts. No REST/UI, scheduler, Kafka or generic queue. No paid calls or real Telegram sends during verification. Success: attributable analysis, safe reruns, visible failure states and working offline/container checks.

Assumptions: feed excerpts suffice if limitations stay visible; unknown dates are excluded. Inspection disproved the supplied assumption of an existing spring-claude dependency. Credentials stay local. No blocking product questions.
