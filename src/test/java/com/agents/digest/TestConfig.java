package com.agents.digest;

import java.net.URI;
import java.time.*;
import java.util.List;

final class TestConfig {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);
    static DigestProperties config(URI endpoint) {
        return new DigestProperties(true, false, "Senior Java Developer", Duration.ofHours(24), 5, 20,
                1500, 40000, Duration.ofSeconds(1), 2000000, List.of(endpoint),
                "mongodb://localhost:27017", "digest_test",
                new DigestProperties.Claude(endpoint, "fixture-key", "fixture-model", 4096, 1,
                        "low", Duration.ofSeconds(1)),
                new DigestProperties.Telegram(endpoint, "fixture-token", "fixture-chat", 1, Duration.ofSeconds(2), Duration.ZERO));
    }
    static News.Candidate candidate(String id) {
        return new News.Candidate(id, "News " + id, "https://example.org/feed",
                "https://example.org/" + id, CLOCK.instant().minusSeconds(60), CLOCK.instant(),
                "Practical verification of agent development", "FEED_EXCERPT");
    }
    static News.Item item(String id, int score) {
        return new News.Item(candidate(id), new News.Analysis(id, score, "Brief summary",
                "Benefits for Java", "Run verification", "Practical impact"), CLOCK.instant());
    }
    static String selection(String id, int score) {
        return """
                {"items":[{"candidateId":"%s","score":%d,"summary":"News summary",
                "relevance":"Benefits for Java","takeaway":"Try it in a project","scoreReason":"Important change"}]}
                """.formatted(id, score);
    }
}
