package com.agents.digest;

import com.agents.digest.News.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class OfflineFixtures {
    private static final AtomicLong IDS = new AtomicLong(1000);
    private OfflineFixtures() {}

    public static List<Candidate> candidates(Clock clock) throws Exception {
        String feed = resource("/fixtures/feed.xml").replace("{{PUBLISHED}}", clock.instant().minusSeconds(3600).toString());
        return FeedSource.parse(feed, "https://example.org/fixture/feed", clock.instant(),
                Duration.ofHours(24), 1000);
    }

    public static String analysis() throws Exception { return resource("/fixtures/analysis.json").replace("{{CANDIDATE_ID}}",
                java.util.UUID.nameUUIDFromBytes("https://example.org/fixture/verification"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()); }

    public static Delivery send(Item item) {
        System.out.println("OFFLINE MESSAGE\n" + MessageFormatter.format(item));
        return Delivery.delivered(IDS.incrementAndGet());
    }

    private static String resource(String path) throws Exception {
        try (var input = OfflineFixtures.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing offline fixture");
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
