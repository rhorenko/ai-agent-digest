package com.agents.digest.news;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class FeedParserTest {
    private final FeedParser parser = new FeedParser();
    private final Instant now = Instant.parse("2026-10-29T12:00:00Z");
    private ByteArrayInputStream input(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }

    @Test void onlyReturnsRecentDatedRssEntries() throws Exception {
        String xml = """
                <rss><channel>
                  <item><title>Fresh</title><link>https://example.org/new</link><pubDate>Thu, 29 Oct 2026 11:00:00 GMT</pubDate><description>Metadata</description></item>
                  <item><title>Old</title><link>https://example.org/old</link><pubDate>Tue, 27 Oct 2026 11:00:00 GMT</pubDate></item>
                  <item><title>Unknown date</title><link>https://example.org/unknown</link></item>
                </channel></rss>
                """;
        var entries = parser.parse(input(xml), "Example", now, now.minusSeconds(86400), 5);
        assertEquals(1, entries.size());
        assertEquals("Fresh", entries.getFirst().title());
        assertEquals("Metadata", entries.getFirst().excerpt());
    }
    @Test void readsAtomAndRejectsExternalEntities() throws Exception {
        String atom = """
                <feed xmlns="http://www.w3.org/2005/Atom"><entry><title>New</title><link href="https://example.org/entry"/><published>2026-10-29T11:00:00Z</published></entry></feed>
                """;
        assertEquals(1, parser.parse(input(atom), "Example", now, now.minusSeconds(86400), 5).size());
        String evil = "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///secret'>]><rss><channel>&x;</channel></rss>";
        assertThrows(Exception.class, () -> parser.parse(input(evil), "Example", now, now.minusSeconds(86400), 5));
    }
}
