package com.agents.digest;

import com.agents.digest.News.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.HttpRequest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QaBoundaryTests {
    @Test void releaseFailureMustNotReportSuccessfulBatch() {
        DigestStore store = mock(DigestStore.class);
        when(store.acquire(anyString())).thenReturn(true);
        when(store.pending(5)).thenReturn(List.of());
        doThrow(new IllegalStateException("db offline")).when(store).release(anyString());
        assertEquals(1, new BatchProcessor(store, List::of, ignored -> List.of(),
                ignored -> Delivery.delivered(1), false, 5).run().exitCode());
    }

    @Test void contextTooSmallMustNotSilentlyDiscardAllCandidates() throws Exception {
        var standard = TestConfig.config(URI.create("http://localhost"));
        var small = new DigestProperties(true, false, "Java", Duration.ofHours(24), 5, 20,
                1500, 1000, standard.timeout(), 2000000, standard.feeds(), standard.mongoUri(), standard.database(),
                standard.claude(), standard.telegram());
        try (var http = new HttpTransport(small.timeout(), small.maxResponseBytes())) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ClaudeAnalyzer(http, small).boundContext(List.of(TestConfig.candidate("one"))));
        }
    }

    @Test void telegramTimeoutIsUnknownAndIsNotRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            try {
                byte[] body = "{\"ok\":true,\"result\":{\"message_id\":42}}".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                Thread.sleep(1400);
                exchange.getResponseBody().write(body);
            }
            catch (Exception ignored) {}
            finally { exchange.close(); }
        });
        server.start();
        var config = TestConfig.config(URI.create("http://localhost:" + server.getAddress().getPort()));
        try (var http = new HttpTransport(config.timeout(), config.maxResponseBytes())) {
            var result = new TelegramSender(http, config, TestConfig.CLOCK, ignored -> {}).send(TestConfig.item("one", 8));
            assertEquals(DeliveryState.UNKNOWN, result.state());
            assertEquals("TELEGRAM_TRANSPORT", result.error());
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @Test void httpRejectsOversizedBodies() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = new byte[2048];
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var http = new HttpTransport(Duration.ofSeconds(2), 1024)) {
            assertThrows(java.io.IOException.class, () -> http.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + server.getAddress().getPort())).timeout(Duration.ofSeconds(2)).GET().build()));
        } finally { server.stop(0); }
    }

    @Test void failedSendingStateWritePreventsNetworkCall() {
        DigestStore store = mock(DigestStore.class);
        when(store.acquire(anyString())).thenReturn(true);
        var item = TestConfig.item("one", 8);
        when(store.pending(5)).thenReturn(List.of(new Pending(item.candidate().url(), item)));
        doThrow(new IllegalStateException("db failed")).when(store).markSending(item.candidate().url());
        BatchProcessor.Sender sender = mock(BatchProcessor.Sender.class);
        assertEquals(1, new BatchProcessor(store, null, null, sender, true, 5).run().exitCode());
        verifyNoInteractions(sender);
    }

    @Test void invalidOfflineDatabaseAndNegativeConfigurationAreRejected() {
        var c = TestConfig.config(URI.create("http://localhost"));
        assertThrows(IllegalArgumentException.class, () -> new DigestProperties(true, false, "Java",
                c.window(), c.maxItems(), c.maxCandidates(), c.excerptChars(), c.maxContextChars(),
                c.timeout(), c.maxResponseBytes(), c.feeds(), c.mongoUri(), "live", c.claude(), c.telegram()));
        assertThrows(IllegalArgumentException.class, () -> new DigestProperties(true, false, "Java",
                Duration.ZERO, c.maxItems(), c.maxCandidates(), c.excerptChars(), c.maxContextChars(),
                c.timeout(), c.maxResponseBytes(), c.feeds(), c.mongoUri(), c.database(), c.claude(), c.telegram()));
    }

    @Test void modelRetryBudgetIsFiniteAndHonorsExplicit429() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("retry-after", "1");
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var c = TestConfig.config(URI.create("http://localhost:" + server.getAddress().getPort()));
        try (var http = new HttpTransport(c.timeout(), c.maxResponseBytes())) {
            assertThrows(IllegalStateException.class, () -> new ClaudeAnalyzer(http, c).analyze(List.of(TestConfig.candidate("one"))));
            assertEquals(2, calls.get());
        } finally { server.stop(0); }
    }

    @Test void longestValidAnalysisFitsOneTelegramMessage() {
        String url = "https://example.org/" + "a".repeat(1180);
        Candidate candidate = new Candidate("id", "T".repeat(300), "https://example.org/feed", url,
                TestConfig.CLOCK.instant(), TestConfig.CLOCK.instant(), "excerpt", "FEED_EXCERPT");
        Analysis analysis = new Analysis("id", 10, "A".repeat(650), "A".repeat(500),
                "A".repeat(500), "A".repeat(350));
        assertTrue(MessageFormatter.format(new Item(candidate, analysis, TestConfig.CLOCK.instant())).length() <= 4096);
    }
}
