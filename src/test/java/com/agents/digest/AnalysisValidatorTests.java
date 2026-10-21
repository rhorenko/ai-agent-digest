package com.agents.digest;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisValidatorTests {
    private final AnalysisValidator validator = new AnalysisValidator();

    @Test void rejectsUnsupportedAndMixedScriptsInEveryAnalysisField() throws Exception {
        var candidates = List.of(TestConfig.candidate("one"));
        var valid = AnalysisValidator.JSON.readTree(TestConfig.selection("one", 8));
        for (String field : List.of("summary", "relevance", "takeaway", "scoreReason")) {
            for (String text : List.of("\u041d\u043e\u0432\u043e\u0441\u0442\u044c", "English \u042f", "\u65b0\u805e", "123")) {
                var invalid = valid.deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("items").get(0)).put(field, text);
                assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid.toString(),
                        candidates, 5, TestConfig.CLOCK.instant()), field);
            }
        }
        EnglishText.require("Java 25: What's new? Caf\u00e9 tools \u2014 tests \u2705");
    }

    @Test void rejectsUnsupportedSourceTitleBeforePersistence() {
        var c = TestConfig.candidate("one");
        var unsupported = new com.agents.digest.News.Candidate(c.id(), "\u042f", c.source(), c.url(),
                c.publishedAt(), c.discoveredAt(), c.excerpt(), c.material());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(TestConfig.selection("one", 8),
                List.of(unsupported), 5, TestConfig.CLOCK.instant()));
    }

    @Test void validatesSelectionAndPreservesRetrievedMetadata() throws Exception {
        var candidate = TestConfig.candidate("one");
        var items = validator.validate(TestConfig.selection("one", 9), List.of(candidate), 5, TestConfig.CLOCK.instant());
        assertEquals(candidate, items.getFirst().candidate());
        assertEquals(9, items.getFirst().analysis().score());
    }

    @Test void irrelevantMaterialCanProduceEmptySelection() throws Exception {
        assertTrue(validator.validate("{\"items\":[]}", List.of(TestConfig.candidate("ad")), 5,
                TestConfig.CLOCK.instant()).isEmpty());
    }

    @Test void rejectsMalformedMissingExtraUnknownDuplicateAndInvalidScores() {
        String valid = TestConfig.selection("one", 8);
        var candidates = List.of(TestConfig.candidate("one"));
        for (String invalid : List.of("not json", "null", "{}", "{\"items\":null}",
                valid.replace("one", "invented"), valid.replace("\"score\":8", "\"score\":0"),
                valid.replace("\"score\":8", "\"score\":11"), valid.replace("\"score\":8", "\"score\":8.5"),
                valid.replace("\"score\":8", "\"score\":\"8\""),
                valid.replace("\"relevance\":\"Benefits for Java\",", ""),
                valid.replace("News summary", ""),
                valid.replace("News summary", "12345"),
                valid.replace("\"score\":8", "\"score\":8,\"url\":\"https://fake.example\""),
                valid.replace("\"score\":8", "\"score\":8,\"score\":9"),
                valid + "{}")) {
            assertThrows(Exception.class, () -> validator.validate(invalid, candidates, 5, TestConfig.CLOCK.instant()), invalid);
        }
        assertThrows(Exception.class, () -> validator.validate(valid, candidates, 0, TestConfig.CLOCK.instant()));
        String duplicate = valid.replace("]}", "," + valid.substring(valid.indexOf('[') + 1));
        assertThrows(Exception.class, () -> validator.validate(duplicate, candidates, 5, TestConfig.CLOCK.instant()));
    }

    @Test void messageIsPlainBoundedAndIncludesLimitationsAndOriginalLink() {
        var item = TestConfig.item("one", 8);
        String message = MessageFormatter.format(item);
        assertTrue(message.length() <= 4096);
        assertTrue(message.contains("8/10"));
        assertTrue(message.contains("Why it matters"));
        assertTrue(message.contains("the full text was not analyzed"));
        assertTrue(message.endsWith(item.candidate().url()));
        var c = item.candidate();
        var headline = new com.agents.digest.News.Candidate(c.id(), c.title(), c.source(), c.url(),
                c.publishedAt(), c.discoveredAt(), "", "HEADLINE_ONLY");
        assertTrue(MessageFormatter.format(new com.agents.digest.News.Item(headline, item.analysis(),
                item.processedAt())).contains("Only the headline is available; the full text was not analyzed."));
    }

    @Test void normalizesTrackingAndPreservesMeaningfulQuery() {
        assertEquals("https://example.org/a?b=2&z=1",
                UrlNormalizer.normalize("HTTPS://EXAMPLE.org:443/x/../a?z=1&utm_source=foo&b=2#fragment"));
        assertThrows(IllegalArgumentException.class, () -> UrlNormalizer.normalize("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> UrlNormalizer.normalize("https://user:pass@example.org/a"));
    }
}
