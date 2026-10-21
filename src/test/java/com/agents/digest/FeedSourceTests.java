package com.agents.digest;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class FeedSourceTests {
    @Test void parsesRssAndAtomAndExcludesUnknownOldFutureDates() throws Exception {
        String rss = """
                <rss><channel>
                <item><title>Useful</title><link>https://example.org/a?utm_source=x</link><pubDate>Wed, 21 Oct 2026 09:00:00 GMT</pubDate><description>&lt;b&gt;Excerpt&lt;/b&gt;</description></item>
                <item><title>Undated</title><link>https://example.org/b</link></item>
                <item><title>Old</title><link>https://example.org/c</link><pubDate>2026-10-19T10:00:00Z</pubDate></item>
                <item><title>Future</title><link>https://example.org/d</link><pubDate>2026-10-22T10:00:00Z</pubDate></item>
                </channel></rss>
                """;
        var items = FeedSource.parse(rss, "https://example.org/rss", TestConfig.CLOCK.instant(), Duration.ofHours(24), 1000);
        assertEquals(1, items.size());
        assertEquals("https://example.org/a", items.getFirst().url());
        assertEquals("Excerpt", items.getFirst().excerpt());
        assertEquals("FEED_EXCERPT", items.getFirst().material());
        assertEquals(1, OfflineFixtures.candidates(TestConfig.CLOCK).size());
    }

    @Test void exactWindowBoundaryQualifiesAndUpdatedDoesNotInventPublication() throws Exception {
        String atom = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                <entry><title>Boundary</title><link href="/boundary"/><published>2026-10-20T10:00:00Z</published></entry>
                <entry><title>Edited</title><link href="/edited"/><updated>2026-10-21T09:00:00Z</updated></entry>
                </feed>
                """;
        var items = FeedSource.parse(atom, "https://example.org/atom", TestConfig.CLOCK.instant(), Duration.ofHours(24), 1000);
        assertEquals(1, items.size());
        assertEquals("HEADLINE_ONLY", items.getFirst().material());
    }

    @Test void rejectsExternalEntities() {
        assertThrows(Exception.class, () -> FeedSource.parse("""
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&xxe;</title></item></channel></rss>
                """, "https://example.org/rss", TestConfig.CLOCK.instant(), Duration.ofHours(24), 1000));
    }
}
