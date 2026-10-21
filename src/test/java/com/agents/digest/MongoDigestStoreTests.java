package com.agents.digest;

import com.agents.digest.News.*;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.testcontainers.mongodb.MongoDBContainer;
import java.util.*;
import static com.mongodb.client.model.Filters.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MongoDigestStoreTests {
    static MongoDBContainer container;
    static MongoClient client;
    MongoDigestStore store;
    MongoDatabase database;

    @BeforeAll static void start() {
        // Docker availability is explicit: a normal verify must exercise persistence.
        // Only an explicitly requested -Ddigest.skipMongoTests=true permits a skip.
        Assumptions.assumeFalse(Boolean.getBoolean("digest.skipMongoTests"), "Mongo tests explicitly disabled");
        container = new MongoDBContainer("mongo:8.0.16");
        container.start();
        client = MongoClients.create(container.getReplicaSetUrl());
    }
    @AfterAll static void stop() {
        if (client != null) client.close();
        if (container != null) container.stop();
    }
    @BeforeEach void setup() {
        database = client.getDatabase("digest_" + UUID.randomUUID().toString().replace("-", "") + "_test");
        store = new MongoDigestStore(database, TestConfig.CLOCK);
        store.initialize();
    }
    @AfterEach void cleanup() { database.drop(); } // Only this test's generated database.

    @Test void persistsRetrievesOrdersAndDeduplicatesWithoutOverwritingDelivery() {
        var high = TestConfig.item("high", 9);
        store.save(TestConfig.item("low", 6));
        store.save(high);
        assertEquals("high", store.pending(5).getFirst().item().candidate().id());
        assertEquals(high, store.pending(5).getFirst().item());
        assertTrue(store.exists(high.candidate().url()));
        store.markSending(high.candidate().url());
        store.recordDelivery(high.candidate().url(), Delivery.delivered(99));
        store.save(high);
        assertEquals(2, database.getCollection("news").countDocuments());
        assertEquals(1, store.pending(5).size());
        assertEquals(99L, database.getCollection("news").find(eq("url", high.candidate().url())).first().getLong("telegramMessageId"));
        assertThrows(com.mongodb.MongoWriteException.class,
                () -> database.getCollection("news").insertOne(new Document("url", high.candidate().url())));
    }

    @Test void twoItemDeliveryLimitLeavesThirdPendingAndSendsHighestScores() {
        store.save(TestConfig.item("low", 6));
        store.save(TestConfig.item("middle", 8));
        store.save(TestConfig.item("high", 9));
        List<String> sent = new ArrayList<>();
        var result = new BatchProcessor(store, null, null, item -> {
            sent.add(item.candidate().id());
            return Delivery.delivered(sent.size());
        }, true, 2).run();
        assertEquals(2, result.delivered());
        assertEquals(List.of("high", "middle"), sent);
        assertEquals(1, store.pending(2).size());
        assertEquals("low", store.pending(2).getFirst().item().candidate().id());
        assertEquals(2, database.getCollection("news").countDocuments(eq("status", "DELIVERED")));
    }

    @Test void atomicLockAndCrashRecoveryExcludeUnknownDeliveries() {
        assertTrue(store.acquire("first"));
        assertFalse(store.acquire("second"));
        store.release("second");
        assertFalse(store.acquire("second"));
        var item = TestConfig.item("one", 8);
        store.save(item);
        store.markSending(item.candidate().url());
        store.recoverInterrupted();
        assertTrue(store.pending(5).isEmpty());
        assertEquals("UNKNOWN", database.getCollection("news").find().first().getString("status"));
        store.release("first");
        assertTrue(store.acquire("second"));
    }

    @Test void offlinePipelineRerunAndSafeRecoveryDoNotCallClaudeAgain() throws Exception {
        var validator = new AnalysisValidator();
        BatchProcessor.Analyzer analyzer = candidates -> validator.validate(OfflineFixtures.analysis(),
                candidates, 5, TestConfig.CLOCK.instant());
        var first = new BatchProcessor(store, () -> OfflineFixtures.candidates(TestConfig.CLOCK), analyzer,
                item -> new Delivery(DeliveryState.RETRY, null, "RATE_LIMIT", TestConfig.CLOCK.instant()), false, 5).run();
        assertEquals(2, first.exitCode());
        var second = new BatchProcessor(store, () -> { throw new AssertionError("must skip source"); },
                candidates -> { throw new AssertionError("must skip Claude"); },
                item -> Delivery.delivered(123), true, 5).run();
        assertEquals(0, second.exitCode());
        assertEquals(1, second.delivered());
        var third = new BatchProcessor(store, () -> OfflineFixtures.candidates(TestConfig.CLOCK),
                candidates -> { throw new AssertionError("dedup must skip Claude"); },
                item -> { throw new AssertionError("must not resend"); }, false, 5).run();
        assertEquals(0, third.exitCode());
        assertEquals(0, third.delivered());
        assertEquals(1, database.getCollection("news").countDocuments());
        assertEquals(3, database.getCollection("runs").countDocuments());
        assertEquals(0, database.getCollection("locks").countDocuments());
    }

    @Test void futureRetryAndUnknownAreExcludedAndRunAuditIsDurable() {
        var item = TestConfig.item("one", 8);
        store.save(item);
        store.markSending(item.candidate().url());
        store.recordDelivery(item.candidate().url(), new Delivery(DeliveryState.RETRY, null, "RATE",
                TestConfig.CLOCK.instant().plusSeconds(60)));
        assertTrue(store.pending(5).isEmpty());
        store.startRun("audit");
        store.finishRun("audit", new Result(2, "PARTIAL", 0, 1), "SAFE_CATEGORY");
        assertEquals(2, database.getCollection("runs").find(eq("_id", "audit")).first().getInteger("exitCode"));
    }

    @Test void telegramAcceptedButSuccessWriteFailedMustNeverBlindlyResend() {
        var item = TestConfig.item("gap", 9);
        store.save(item);
        DigestStore failingStore = spy(store);
        doThrow(new IllegalStateException("database unavailable"))
                .when(failingStore).recordDelivery(anyString(), any());
        var first = new BatchProcessor(failingStore, null, null,
                ignored -> Delivery.delivered(555), true, 5).run();
        assertEquals(1, first.exitCode());
        assertEquals("SENDING", database.getCollection("news").find().first().getString("status"));
        var next = new BatchProcessor(store, null, null,
                ignored -> { throw new AssertionError("ambiguous send must not retry"); }, true, 5).run();
        assertEquals(2, next.exitCode());
        assertEquals("UNKNOWN", database.getCollection("news").find().first().getString("status"));
    }
}
