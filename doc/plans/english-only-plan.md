# English-only news delivery

User request, 2026-10-22: replace Russian text and language logic with English.

1. Update FEAT-001-AC-02/03/06 to require English selection, analysis and delivery.
2. Translate prompts, formatter, fixtures and test data. Require Latin-script prose and titles before persistence and delivery, including stored recovery items. Ask the model to select English source material only.
3. Test valid English, unsupported/mixed scripts, every analysis field, source titles, recovery delivery rejection and message boundaries.
4. Run focused tests, Maven verify, offline packaged startup and container checks where available. Record actual evidence and limitations.

English semantics are enforced by the model prompt; script validation cannot distinguish English from other Latin-script languages. Source metadata stays verbatim. Existing stored records are not migrated.
