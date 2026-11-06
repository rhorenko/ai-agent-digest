package com.agents.digest.news;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisValidatorTest {
    private final Instant now = Instant.parse("2026-10-29T12:00:00Z");
    private final Candidate source = new Candidate("1", "Title", "Source", "https://EXAMPLE.org:443/a#section", now, now, "Excerpt");
    private final Analysis valid = new Analysis("1", 8, "Summary", "Relevance", "Action", "Reason");
    private final AnalysisValidator validator = new AnalysisValidator();

    @Test void acceptsEmptyAndValidSelection() {
        assertTrue(validator.validate(List.of(source), List.of(), 5, now.minusSeconds(86400), now).isEmpty());
        assertEquals(source, validator.validate(List.of(source), List.of(valid), 5, now.minusSeconds(86400), now).getFirst().candidate());
    }
    @Test void rejectsInventedIdsAndMissingFields() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(source), List.of(new Analysis("other", 8, "s", "r", "t", "e")), 5, now.minusSeconds(86400), now));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(source), List.of(new Analysis("1", 8, "", "r", "t", "e")), 5, now.minusSeconds(86400), now));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(source), List.of(new Analysis("1", 11, "s", "r", "t", "e")), 5, now.minusSeconds(86400), now));
    }
    @Test void rejectsOutOfWindowAndDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(source), List.of(valid), 5, now.plusSeconds(1), now.plusSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(source), List.of(valid, valid), 5, now.minusSeconds(86400), now));
    }
    @Test void normalizesWithoutDiscardingQuery() {
        assertEquals("https://example.org/a?q=1", NewsUrl.normalize("HTTPS://EXAMPLE.ORG:443/x/../a?q=1#fragment"));
        assertThrows(IllegalArgumentException.class, () -> NewsUrl.normalize("file:///secrets"));
        assertThrows(IllegalArgumentException.class, () -> NewsUrl.normalize("https://user@example.org/"));
    }
}
