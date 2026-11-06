# Architecture decision: source-bound analysis

Status: accepted, 2026-10-29.

The existing Maven dependency tree contains neither a `spring-claude` artifact nor a configured Claude client. There is consequently no existing library API or search capability to reuse. Anthropic's Messages API documents an explicit web-search tool and structured JSON output; they are not automatic properties of an ordinary model call. The structured-output documentation also states that citations are incompatible with JSON output. For an auditable first implementation, retrieve bounded RSS/Atom entries from configured official feeds and provide only those candidate IDs and metadata to Claude. Validate every selected ID, field and score before persistence. This trades search breadth and full-text analysis for verifiable provenance and a smaller integration surface.

Sources: https://platform.claude.com/docs/en/build-with-claude/structured-outputs and https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool.

Use a MongoDB unique normalized URL and persistent delivery states, with an execution lock. A delivery whose Telegram outcome is uncertain is not automatically retried. A Telegram success followed by a failed database update is also uncertain: no exactly-once guarantee is possible. Existing data and volumes must be retained during testing and rollback.

