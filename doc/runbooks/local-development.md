# Local development and source coverage

JDK 25 and Maven Wrapper are required. Build with .\mvnw.cmd verify. Start Compose Mongo and run the executable JAR as shown in README. Java does not import dotenv: export environment variables or use an untracked external YAML through --spring.config.additional-location=file:....

## Sources

The 20 default feeds below were checked with the application's Java HTTP/parser/HEAD path on 2026-10-22: 20 successful feeds, 60 verified candidates from 20 sources; context bounding retained 54 candidates from all 20 sources (39974 characters). This is point-in-time availability, not a guarantee of future availability or model relevance. Python probes received 403 for some feeds that Java successfully retrieved.

| Source | Feed | Coverage |
|---|---|---|
| GitHub changelog | [https://github.blog/changelog/feed/](https://github.blog/changelog/feed/) | Copilot and platform releases |
| GitHub AI/ML | [https://github.blog/ai-and-ml/feed/](https://github.blog/ai-and-ml/feed/) | AI coding and agent workflows |
| GitHub engineering | [https://github.blog/engineering/feed/](https://github.blog/engineering/feed/) | Engineering and infrastructure |
| OpenAI news | [https://openai.com/news/rss.xml](https://openai.com/news/rss.xml) | Official models and products |
| Simon Willison | [https://simonwillison.net/atom/entries/](https://simonwillison.net/atom/entries/) | LLMs and practical experiments |
| Hugging Face | [https://huggingface.co/blog/feed.xml](https://huggingface.co/blog/feed.xml) | Open models and tools |
| Google AI | [https://blog.google/innovation-and-ai/technology/ai/rss/](https://blog.google/innovation-and-ai/technology/ai/rss/) | AI products and announcements |
| Google Research | [https://research.google/blog/rss/](https://research.google/blog/rss/) | Research and AI systems |
| Microsoft Research | [https://www.microsoft.com/en-us/research/feed/](https://www.microsoft.com/en-us/research/feed/) | Research and developer tooling |
| Microsoft Agent Framework | [https://devblogs.microsoft.com/agent-framework/feed/](https://devblogs.microsoft.com/agent-framework/feed/) | Agents and orchestration |
| NVIDIA | [https://blogs.nvidia.com/feed/](https://blogs.nvidia.com/feed/) | AI models and infrastructure |
| AWS ML | [https://aws.amazon.com/blogs/machine-learning/feed/](https://aws.amazon.com/blogs/machine-learning/feed/) | Applied AI and production systems |
| JetBrains AI | [https://blog.jetbrains.com/ai/feed/](https://blog.jetbrains.com/ai/feed/) | AI coding tools and agents |
| IntelliJ IDEA | [https://blog.jetbrains.com/idea/feed/](https://blog.jetbrains.com/idea/feed/) | Java IDE and AI tooling |
| Spring | [https://spring.io/blog.atom](https://spring.io/blog.atom) | Spring ecosystem |
| Inside Java | [https://inside.java/feed.xml](https://inside.java/feed.xml) | Java and JVM |
| Docker | [https://www.docker.com/feed/](https://www.docker.com/feed/) | Containers and developer tools |
| InfoQ | [https://feed.infoq.com/](https://feed.infoq.com/) | Software engineering and AI |
| Stack Overflow | [https://stackoverflow.blog/feed/](https://stackoverflow.blog/feed/) | Development workflows |
| Ollama | [https://ollama.com/blog/rss.xml](https://ollama.com/blog/rss.xml) | Local models and agents |

Claude Code releases and Martin Fowler's Atom currently expose only `updated`, not `published`; they are not defaults because the parser never invents a publication date from an edit timestamp. Some old feed URLs redirect or no longer return XML, so the list uses tested canonical endpoints. Coverage is not exhaustive and there is no general web search. A normal Claude Messages call has no automatic current-news access.

RSS/Atom parsing disables DTD/entities. Dates use RSS pubDate or Atom published, never Atom updated. Undated, old and future entries are excluded; the lower window boundary is inclusive. Summary/description is markup-stripped and bounded; missing excerpts are labeled HEADLINE_ONLY. The 30-day window filters items present in the current feed response; feeds may expose only their latest entries, not a complete month-long archive. Full articles are not analyzed.

Article URLs are normalized and verified by HEAD returning 2xx. A single permanent 301/308 redirect may add only a trailing slash to the exact same URL (same scheme, authority and query); this is logged and the destination must return 2xx. Original feed URL/provenance is retained. All other redirects, HEAD-rejecting servers and inaccessible URLs are excluded. No GET fallback. See [ADR 0002](../adr/0002-source-coverage.md). Host/status/rejection logs explain failures without full URLs or response bodies. Individual article failures do not discard the rest of the feed. If all feeds fail HTTP/XML parsing, the batch fails.

All feeds are fetched before link checks. Candidates are checked round-robin, one entry per source per round, newest first within each feed. Duplicate URLs do not consume additional checks. At most `max-candidates` unique original URLs are checked (60 by default); permitted canonical-slash verification can add one HEAD per candidate. Feed order breaks ties when the budget is smaller than the number of sources. Context/output bounds still apply; source diversity does not guarantee a selected item from every feed.

## Window and output configuration

| Setting | Environment | Default | Meaning |
|---|---|---|---|
| `digest.window` | `DIGEST_WINDOW` | `30d` | Rolling lookback, maximum 30 days |
| `digest.max-items` | `DIGEST_MAX_ITEMS` | `2` | Maximum new selections and total deliveries per run |
| `digest.max-candidates` | `DIGEST_MAX_CANDIDATES` | `60` | Unique candidate link checks across feeds |
| `digest.feeds` | `DIGEST_FEEDS` | 20 feeds above | Comma-separated HTTPS feed URLs, 1-30 sources |
| `digest.max-context-chars` | `DIGEST_MAX_CONTEXT_CHARS` | `40000` | Separate bound on the model input |

Repository defaults: `src/main/resources/application.yaml`; Docker overrides/defaults: `docker/compose.yaml`; environment examples: `.env.example`. In IntelliJ use Run Configuration > Program arguments: `--digest.window=30d --digest.max-items=2 --digest.max-candidates=60`, or set the corresponding environment variables. Existing IDE/environment/external YAML overrides take precedence over repository defaults. Remove old `DIGEST_FEEDS` overrides to get the new 20-source default list. The `local` profile alone does not load `.env`; Java does not import dotenv automatically. CLI arguments override environment settings.

## Claude and runtime prompts

There was no spring-claude dependency/client/configuration to reuse. JDK HttpClient posts to /v1/messages with x-api-key, anthropic-version=2023-06-01, externally configured model/max_tokens/timeout, output_config.format=json_schema and optional output_config.effort (CLAUDE_EFFORT, default low). Thinking blocks in the response are ignored; exactly one text block must carry the JSON. The existing Boot BOM resolves Mongo driver 5.6.4, Jackson databind 2.21.2 and Testcontainers Mongo 2.0.4.

Versioned resources: src/main/resources/prompts/analysis-v1.txt and analysis-v1.schema.json. Candidate text is untrusted JSON data, never instructions. One request selects candidate IDs and English summary/relevance/takeaway/short score reasons, without hidden reasoning. Java enforces exact fields, prose bounds and Latin-script titles/analysis, IDs/uniqueness, integer score 1РІР‚вЂњ10 and maximum count. Titles/links/dates/source come only from retrieved candidates.

Only explicit Claude 429/5xx retries, with bounded 0РІР‚вЂњ3 retries (default one). Numeric retry-after up to 30 seconds is honored; longer or date-form delays fail for later recovery. Invalid/truncated/refused responses, transport timeouts and auth errors never retry. Transient retries can still cost money; configure zero retries if desired.

Default context bound is 40000 characters, not an exact token count; output maximum is 4096 tokens. Too-small context fails explicitly. Model factual quality, relevance filtering and language quality require live evaluation; fixture tests prove contracts and safety.

## Configuration and offline fixtures

DigestProperties validates ranges and conditionally requires live credentials. .env.example lists all environment names. DIGEST_PROFILE, DIGEST_WINDOW, DIGEST_MAX_ITEMS and DIGEST_FEEDS need no Java changes. Timeouts default 30s (1sРІР‚вЂњ3m), max candidates <=100, max items <=20 and <= candidates. Recovery-only does not require a Claude key/model.

Secrets are external; no credentials enter the image. Do not enable HTTP wire/debug logs or print populated environment/Compose output. Default driver/config diagnostic logs are suppressed; stored error categories avoid credential-bearing exception text.

Offline fixtures in src/main/resources/fixtures use a synthetic example.org article and relative publication time. The stable URL/ID permits deterministic reruns. Offline URLs intentionally bypass network verification; offline analysis still passes the same validator and storage/delivery processor.

English is the only supported language. The prompt selects English titles/source material and produces English prose. The script guard rejects non-Latin letters before persistence and again at delivery, including recovery. It cannot distinguish English from other Latin-script languages. Existing records are not migrated.

## Pipeline logs

INFO logs are enabled by default. Startup reports LIVE/OFFLINE and recovery-only mode, source/window/item limits. Processing logs database initialization, lock/recovery, each feed HTTP status and eligible counts, round-robin link-check results, deduplication, Claude context/request attempts/status/time, validated selection count, persistence, delivery queue/progress/outcomes and total elapsed milliseconds. WARN covers unavailable sources, retries, busy locks and unsuccessful Telegram outcomes with locally generated error categories; ERROR identifies the failed batch stage and exception type.

A successful empty run now explains why: `NO_CANDIDATES` (no usable feed candidates), `ALL_ALREADY_STORED` (deduplicated against Mongo), or `MODEL_SELECTED_ZERO` (analysis selected nothing). `RECOVERY_ONLY` skips retrieval/Claude; `NO_PENDING_ITEMS` explains an empty delivery queue. `eligibleEntries=0` means no entries survived parsing/date/window checks; Rejected links log their host/status at INFO. Duplicate URLs and the global candidate limit can also reduce candidate counts. The reason concerns new items; existing pending deliveries are still processed.

Original-link HEAD host/status results are now visible at INFO without extra flags. Application logs omit API keys, tokens, chat IDs, database URIs, full URLs, request/response bodies and exception messages/stacks. Offline fixture message output remains visible as before. Avoid enabling HTTP wire logs.
