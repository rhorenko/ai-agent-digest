package com.agents.digest;

import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpIntegrationTests {
    private HttpServer server;
    private HttpTransport http;
    private DigestProperties config;
    private final List<String> requests = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> response = new AtomicReference<>("{\"ok\":true,\"result\":{\"message_id\":42}}");
    private int status = 200;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            calls.incrementAndGet();
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        config = TestConfig.config(URI.create("http://localhost:" + server.getAddress().getPort()));
        http = new HttpTransport(config.timeout(), config.maxResponseBytes());
    }

    @AfterEach void stop() { http.close(); server.stop(0); }

    private TelegramSender sender(List<Duration> sleeps) {
        return new TelegramSender(http, config, TestConfig.CLOCK, sleeps::add);
    }

    @Test void networkProgressLogsExposeStatusButNotCredentialsOrPayloads() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.agents.digest");
        var previous = logger.getLevel();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        logger.addAppender(appender);
        try {
            var claude = new ClaudeAnalyzer(http, config);
            var candidates = List.of(TestConfig.candidate("one"));
            response.set(AnalysisValidator.JSON.writeValueAsString(Map.of("stop_reason", "end_turn", "content",
                    List.of(Map.of("type", "text", "text", TestConfig.selection("one", 8))))));
            claude.analyze(claude.boundContext(candidates));
            response.set("{\"ok\":true,\"result\":{\"message_id\":42}}");
            sender(new ArrayList<>()).send(TestConfig.item("one", 8));
            response.set("<rss><channel/></rss>");
            new FeedSource(http, config, TestConfig.CLOCK).retrieve();
            String logs = String.join("\n", appender.list.stream().map(
                    ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList());
            assertTrue(logs.contains("Claude context bounded: input=1 included=1 excluded=0"));
            assertTrue(logs.contains("Claude request started: model=fixture-model candidates=1 attempt=1/2"));
            assertTrue(logs.contains("Claude response received: status=200 elapsedMs="));
            assertTrue(logs.contains("Telegram response received: status=200 elapsedMs="));
            assertTrue(logs.contains("Feed parsed: feed=1 eligibleEntries=0"));
            assertTrue(logs.contains("successfulFeeds=1 failedFeeds=0 checkedLinks=0 candidates=0"));
            for (String sensitive : List.of(config.claude().apiKey(), config.telegram().botToken(),
                    config.telegram().chatId(), candidates.getFirst().url(), candidates.getFirst().title(),
                    TestConfig.item("one", 8).analysis().summary(), TestConfig.selection("one", 8))) {
                assertFalse(logs.contains(sensitive), "Sensitive value appeared in network logs");
            }
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
        }
    }

    @Test void storedUnsupportedProseOrTitleIsRejectedWithoutSending() {
        var item = TestConfig.item("one", 8);
        var c = item.candidate();
        var a = item.analysis();
        var oldAnalysis = new News.Analysis(a.candidateId(), a.score(), "\u042f", a.relevance(), a.takeaway(), a.scoreReason());
        var oldTitle = new News.Candidate(c.id(), "\u042f", c.source(), c.url(), c.publishedAt(),
                c.discoveredAt(), c.excerpt(), c.material());
        for (var old : List.of(new News.Item(c, oldAnalysis, item.processedAt()),
                new News.Item(oldTitle, a, item.processedAt()))) {
            var result = sender(new ArrayList<>()).send(old);
            assertEquals(News.DeliveryState.REJECTED, result.state());
            assertEquals("TELEGRAM_INVALID_MESSAGE", result.error());
        }
        assertEquals(0, calls.get());
    }

    @Test void telegramSendsPlainTextAndStoresMessageId() {
        var delivery = sender(new ArrayList<>()).send(TestConfig.item("one", 8));
        assertEquals(News.DeliveryState.DELIVERED, delivery.state());
        assertEquals(42L, delivery.messageId());
        assertTrue(requests.getFirst().contains("Why it matters"));
        assertFalse(requests.getFirst().contains("parse_mode"));
        assertEquals(1, calls.get());
    }

    @Test void telegramRetriesRateLimitWithinBudgetAndDefersLongerDelay() {
        status = 429;
        response.set("{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":2}}");
        List<Duration> sleeps = new ArrayList<>();
        var result = sender(sleeps).send(TestConfig.item("one", 8));
        assertEquals(News.DeliveryState.RETRY, result.state());
        assertEquals(2, calls.get());
        assertTrue(sleeps.contains(Duration.ofSeconds(2)));
        assertEquals(TestConfig.CLOCK.instant().plusSeconds(2), result.retryAt());
        response.set("{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":60}}");
        result = sender(sleeps).send(TestConfig.item("one", 8));
        assertEquals(TestConfig.CLOCK.instant().plusSeconds(60), result.retryAt());
        assertEquals(3, calls.get());
    }

    @Test void telegramServerFailureMalformedAndRejectedResponsesAreDistinctWithoutBlindRetries() {
        status = 500;
        response.set("{\"ok\":false}");
        assertEquals(News.DeliveryState.UNKNOWN, sender(new ArrayList<>()).send(TestConfig.item("one", 8)).state());
        status = 200;
        response.set("not json");
        assertEquals(News.DeliveryState.UNKNOWN, sender(new ArrayList<>()).send(TestConfig.item("one", 8)).state());
        status = 400;
        response.set("{\"ok\":false,\"error_code\":400}");
        assertEquals(News.DeliveryState.REJECTED, sender(new ArrayList<>()).send(TestConfig.item("one", 8)).state());
        assertEquals(3, calls.get());
    }

    @Test void claudeRequestUsesRealStructuredHttpContractAndVersionedPrompt() throws Exception {
        response.set(AnalysisValidator.JSON.writeValueAsString(Map.of("stop_reason", "end_turn", "content",
                List.of(Map.of("type", "text", "text", TestConfig.selection("one", 8))))));
        var claude = new ClaudeAnalyzer(http, config);
        String selection = claude.analyze(List.of(TestConfig.candidate("one")));
        assertEquals(1, new AnalysisValidator().validate(selection, List.of(TestConfig.candidate("one")), 5,
                TestConfig.CLOCK.instant()).size());
        var request = AnalysisValidator.JSON.readTree(requests.getFirst());
        assertEquals("fixture-model", request.path("model").asText());
        assertEquals("json_schema", request.path("output_config").path("format").path("type").asText());
        assertTrue(request.path("system").asText().contains("untrusted"));
        assertTrue(request.path("system").asText().contains("All output prose must be English"));
        assertTrue(request.path("system").asText().contains("English titles and English source material"));
        assertFalse(request.has("tools"));
        assertEquals(4096, request.path("max_tokens").asInt());
        assertEquals("low", request.path("output_config").path("effort").asText());
    }

    @Test void claudeThinkingBlocksAreIgnoredAndOnlyOneTextBlockIsAccepted() throws Exception {
        var claude = new ClaudeAnalyzer(http, config);
        Map<String, Object> thinking = Map.of("type", "thinking", "thinking", "", "signature", "sig");
        Map<String, Object> text = Map.of("type", "text", "text", TestConfig.selection("one", 8));
        response.set(AnalysisValidator.JSON.writeValueAsString(Map.of("stop_reason", "end_turn",
                "content", List.of(thinking, text))));
        assertEquals(TestConfig.selection("one", 8), claude.analyze(List.of(TestConfig.candidate("one"))));
        for (var content : List.of(List.of(thinking), List.of(text, text),
                List.of(thinking, Map.of("type", "tool_use", "id", "x", "name", "y", "input", Map.of()), text))) {
            response.set(AnalysisValidator.JSON.writeValueAsString(Map.of("stop_reason", "end_turn", "content", content)));
            var error = assertThrows(IllegalArgumentException.class, () -> claude.analyze(List.of(TestConfig.candidate("one"))));
            assertEquals("CLAUDE_INVALID_CONTENT", error.getMessage());
        }
    }

    @Test void blankEffortIsOmittedAndUnknownEffortIsRejected() throws Exception {
        var c = config.claude();
        var omitted = withClaude(new DigestProperties.Claude(c.endpoint(), c.apiKey(), c.model(), c.maxTokens(),
                c.retries(), "", c.timeout()));
        response.set(AnalysisValidator.JSON.writeValueAsString(Map.of("stop_reason", "end_turn", "content",
                List.of(Map.of("type", "text", "text", TestConfig.selection("one", 8))))));
        new ClaudeAnalyzer(http, omitted).analyze(List.of(TestConfig.candidate("one")));
        assertFalse(AnalysisValidator.JSON.readTree(requests.getFirst()).path("output_config").has("effort"));
        assertThrows(IllegalArgumentException.class, () -> withClaude(new DigestProperties.Claude(c.endpoint(),
                c.apiKey(), c.model(), c.maxTokens(), c.retries(), "extreme", c.timeout())));
        assertThrows(IllegalArgumentException.class, () -> withClaude(new DigestProperties.Claude(c.endpoint(),
                c.apiKey(), c.model(), c.maxTokens(), c.retries(), "low", Duration.ZERO)));
    }

    private DigestProperties withClaude(DigestProperties.Claude claude) {
        var c = config;
        return new DigestProperties(c.offline(), c.recoveryOnly(), c.profile(), c.window(), c.maxItems(),
                c.maxCandidates(), c.excerptChars(), c.maxContextChars(), c.timeout(), c.maxResponseBytes(),
                c.feeds(), c.mongoUri(), c.database(), claude, c.telegram());
    }

    @Test void claudeRefusalAndTruncationAreRejectedWithoutRetry() throws Exception {
        var claude = new ClaudeAnalyzer(http, config);
        response.set("{\"stop_reason\":\"max_tokens\",\"content\":[]}");
        assertThrows(IllegalArgumentException.class, () -> claude.analyze(List.of(TestConfig.candidate("one"))));
        status = 401;
        assertThrows(IllegalStateException.class, () -> claude.analyze(List.of(TestConfig.candidate("one"))));
        assertEquals(2, calls.get());
    }

    @Test void sourceRetrievalVerifiesOriginalLinkAndExcludesFailedVerification() throws Exception {
        String article = config.claude().endpoint() + "/article";
        response.set("""
                <rss><channel><item><title>Engineering change</title><link>%s</link>
                <pubDate>Wed, 21 Oct 2026 09:00:00 GMT</pubDate><description>Practical evidence</description>
                </item></channel></rss>
                """.formatted(article));
        AtomicInteger heads = new AtomicInteger();
        server.createContext("/article", exchange -> {
            heads.incrementAndGet();
            assertEquals("HEAD", exchange.getRequestMethod());
            exchange.sendResponseHeaders(heads.get() == 1 ? 200 : 403, -1);
            exchange.close();
        });
        var source = new FeedSource(http, config, TestConfig.CLOCK);
        assertEquals(article, source.retrieve().getFirst().url());
        assertTrue(source.retrieve().isEmpty());
        assertEquals(2, heads.get());
        status = 503;
        assertThrows(IllegalStateException.class, source::retrieve);
    }
}
