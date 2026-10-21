package com.agents.digest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SourceExpansionTests {
    private HttpServer server;
    private URI base;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        base = URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void shippedDefaultsBindTwentySourcesThirtyDaysAndTwoItems() throws Exception {
        var environment = new MockEnvironment().withProperty("digest.offline", "true")
                .withProperty("digest.database", "defaults_test");
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        var config = Binder.get(environment).bind("digest", Bindable.of(DigestProperties.class)).get();
        assertEquals(20, config.feeds().size());
        assertEquals(20, new HashSet<>(config.feeds()).size());
        assertEquals(Duration.ofDays(30), config.window());
        assertEquals(2, config.maxItems());
        assertEquals(60, config.maxCandidates());
        environment.setProperty("DIGEST_WINDOW", "7d");
        environment.setProperty("DIGEST_MAX_ITEMS", "1");
        environment.setProperty("DIGEST_FEEDS", "https://example.org/one,https://example.org/two");
        var overridden = Binder.get(environment).bind("digest", Bindable.of(DigestProperties.class)).get();
        assertEquals(Duration.ofDays(7), overridden.window());
        assertEquals(1, overridden.maxItems());
        assertEquals(2, overridden.feeds().size());
    }

    @Test void liveConfigAcceptsTwentyAndThirtyFeedsButRejectsThirtyOneOrNone() {
        for (int count : List.of(20, 30)) assertEquals(count, liveConfig(count).feeds().size());
        assertThrows(IllegalArgumentException.class, () -> liveConfig(31));
        assertThrows(IllegalArgumentException.class, () -> liveConfig(0));
    }

    private DigestProperties liveConfig(int count) {
        var c = TestConfig.config(URI.create("https://example.org"));
        var feeds = java.util.stream.IntStream.range(0, count).mapToObj(i -> URI.create("https://example.org/feed" + i)).toList();
        return new DigestProperties(false, false, c.profile(), Duration.ofDays(30), 2, 60, c.excerptChars(),
                c.maxContextChars(), c.timeout(), c.maxResponseBytes(), feeds, c.mongoUri(), c.database(), c.claude(), c.telegram());
    }

    @Test void twoItemSelectionLimitRejectsAThirdItem() throws Exception {
        var candidates = List.of(TestConfig.candidate("a"), TestConfig.candidate("b"), TestConfig.candidate("c"));
        var items = AnalysisValidator.JSON.createArrayNode();
        for (String id : List.of("a", "b")) items.add(AnalysisValidator.JSON.readTree(TestConfig.selection(id, 8)).path("items").get(0));
        var selection = AnalysisValidator.JSON.createObjectNode().set("items", items);
        var validator = new AnalysisValidator();
        assertEquals(2, validator.validate(selection.toString(), candidates, 2, TestConfig.CLOCK.instant()).size());
        items.add(AnalysisValidator.JSON.readTree(TestConfig.selection("c", 8)).path("items").get(0));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(selection.toString(), candidates, 2, TestConfig.CLOCK.instant()));
    }

    @Test void rollingMonthIncludesExactBoundaryAndExcludesOlderFutureAndUndated() throws Exception {
        String xml = "<rss><channel>";
        for (var pair : List.of(new String[]{"boundary", "2026-09-21T10:00:00Z"},
                new String[]{"old", "2026-09-21T09:59:59Z"}, new String[]{"recent", "2026-10-20T10:00:00Z"},
                new String[]{"future", "2026-10-21T10:00:01Z"}, new String[]{"undated", ""})) {
            xml += "<item><title>" + pair[0] + "</title><link>https://example.org/" + pair[0]
                    + "</link><pubDate>" + pair[1] + "</pubDate></item>";
        }
        var result = FeedSource.parse(xml + "</channel></rss>", "https://example.org/feed",
                TestConfig.CLOCK.instant(), Duration.ofDays(30), 1500);
        assertEquals(List.of("recent", "boundary"), result.stream().map(News.Candidate::title).toList());
    }

    @Test void limitedBudgetIncludesLaterFeedsBeforeSecondEntryFromFirstFeed() throws Exception {
        feed("/first", "/a", "/b", "/c");
        feed("/second", "/d", "/e");
        var heads = new AtomicInteger();
        for (String path : List.of("/a", "/b", "/c", "/d", "/e")) head(path, heads);
        var result = retrieve(List.of(base.resolve("/first"), base.resolve("/second")), 2);
        assertEquals(List.of(base + "/a", base + "/d"), result.stream().map(News.Candidate::url).toList());
        assertEquals(2, heads.get());
    }

    @Test void badArticleDoesNotDiscardLaterEntriesAndDuplicateDoesNotConsumeBudget() throws Exception {
        feed("/first", "/bad", "/good");
        feed("/second", "/bad", "/other");
        server.createContext("/bad", exchange -> {
            exchange.getResponseHeaders().add("Location", "[");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        var heads = new AtomicInteger();
        head("/good", heads);
        head("/other", heads);
        var result = retrieve(List.of(base.resolve("/first"), base.resolve("/second")), 3);
        assertEquals(2, result.size());
        assertEquals(2, heads.get());
    }

    @Test void onlyOnePermanentTrailingSlashRedirectIsAllowedAndOriginalUrlIsRetained() throws Exception {
        feed("/feed", "/article");
        var location = new AtomicReference<>("/article/");
        var status = new AtomicInteger(301);
        var canonicalStatus = new AtomicInteger(200);
        var canonicalCalls = new AtomicInteger();
        server.createContext("/article", exchange -> {
            exchange.getResponseHeaders().add("Location", location.get());
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.createContext("/article/", exchange -> {
            canonicalCalls.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "/elsewhere");
            exchange.sendResponseHeaders(canonicalStatus.get(), -1);
            exchange.close();
        });
        for (int redirect : List.of(301, 308)) {
            status.set(redirect);
            assertEquals(base + "/article", retrieve(List.of(base.resolve("/feed")), 2).getFirst().url());
        }
        for (String target : List.of("https://other.example/article/", "/other/", "/article/?new=query",
                "http://user:password@localhost:" + base.getPort() + "/article/", "https://localhost:" + base.getPort() + "/article/")) {
            location.set(target);
            assertTrue(retrieve(List.of(base.resolve("/feed")), 2).isEmpty());
        }
        assertEquals(2, canonicalCalls.get());
        location.set("/article/");
        status.set(302);
        assertTrue(retrieve(List.of(base.resolve("/feed")), 2).isEmpty());
        status.set(301);
        for (int code : List.of(301, 403, 405, 500)) {
            canonicalStatus.set(code);
            assertTrue(retrieve(List.of(base.resolve("/feed")), 2).isEmpty());
        }
    }

    private void feed(String path, String... articles) {
        server.createContext(path, exchange -> {
            String xml = "<rss><channel>";
            for (String article : articles) xml += "<item><title>Engineering news</title><link>" + base + article
                    + "</link><pubDate>2026-10-21T09:00:00Z</pubDate></item>";
            byte[] bytes = (xml + "</channel></rss>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private void head(String path, AtomicInteger calls) {
        server.createContext(path, exchange -> {
            assertEquals("HEAD", exchange.getRequestMethod());
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
    }

    private List<News.Candidate> retrieve(List<URI> feeds, int limit) throws Exception {
        var c = TestConfig.config(base);
        var config = new DigestProperties(true, false, c.profile(), Duration.ofDays(30), 2, limit,
                c.excerptChars(), c.maxContextChars(), c.timeout(), c.maxResponseBytes(), feeds,
                c.mongoUri(), c.database(), c.claude(), c.telegram());
        try (var http = new HttpTransport(config.timeout(), config.maxResponseBytes())) {
            return new FeedSource(http, config, TestConfig.CLOCK).retrieve();
        }
    }
}
