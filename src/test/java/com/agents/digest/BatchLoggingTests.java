package com.agents.digest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchLoggingTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger(BatchProcessor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previous;

    @BeforeEach void capture() {
        previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach void restore() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previous);
    }

    @Test void emptySourcesExplainWhyAnalysisAndDeliveryWereSkipped() {
        run(readyStore(), List::of, candidates -> fail("Unexpected analysis"), false);
        assertLog("retrieved=0 alreadyStored=0 fresh=0");
        assertLog("Analysis skipped: reason=NO_CANDIDATES");
        assertLog("Delivery skipped: reason=NO_PENDING_ITEMS");
        assertLog("Batch completed: status=SUCCESS delivered=0 unresolved=0 exit=0 elapsedMs=");
    }

    @Test void storedSourcesExplainDeduplicationWithoutAnalysis() {
        var store = readyStore();
        when(store.exists(anyString())).thenReturn(true);
        run(store, () -> List.of(TestConfig.candidate("one")), candidates -> fail("Unexpected analysis"), false);
        assertLog("retrieved=1 alreadyStored=1 fresh=0");
        assertLog("Analysis skipped: reason=ALL_ALREADY_STORED");
    }

    @Test void zeroSelectionIsDistinctFromNoCandidates() {
        run(readyStore(), () -> List.of(TestConfig.candidate("one")), candidates -> List.of(), false);
        assertLog("retrieved=1 alreadyStored=0 fresh=1");
        assertLog("Analysis and validation completed: selected=0");
        assertLog("reason=MODEL_SELECTED_ZERO");
    }

    @Test void failureIdentifiesStageWithoutLeakingExceptionCredentials() {
        var store = readyStore();
        var result = run(store, () -> { throw new IllegalStateException("secret-key https://user:password@host"); },
                candidates -> List.of(), false);
        assertEquals(1, result.exitCode());
        assertLog("Batch failed: stage=source-retrieval errorType=IllegalStateException");
        assertLog("Database lock released");
        assertFalse(logs().contains("secret-key"));
        assertFalse(logs().contains("password"));
        assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
    }

    @Test void recoveryShowsDeliveryProgressAndOutcomeWithoutSourceContent() {
        var store = readyStore();
        var item = TestConfig.item("one", 8);
        when(store.pending(5)).thenReturn(List.of(new News.Pending(item.candidate().url(), item)));
        var result = run(store, () -> fail("Unexpected retrieval"), candidates -> fail("Unexpected analysis"), true);
        assertEquals(1, result.delivered());
        assertLog("reason=RECOVERY_ONLY");
        assertLog("Delivery queue loaded: pending=1 limit=5");
        assertLog("Delivery started: item=1/1");
        assertLog("Delivery completed: item=1/1 state=DELIVERED");
        assertFalse(logs().contains(item.candidate().url()));
        assertFalse(logs().contains(item.analysis().summary()));
    }

    @Test void busyLockIsExplicitAndDoesNotRetrieve() {
        var store = mock(DigestStore.class);
        assertEquals(3, run(store, () -> fail("Unexpected retrieval"), candidates -> List.of(), false).exitCode());
        assertLog("another run holds the database lock (BUSY)");
    }

    private DigestStore readyStore() {
        var store = mock(DigestStore.class);
        when(store.acquire(anyString())).thenReturn(true);
        when(store.pending(5)).thenReturn(List.of());
        return store;
    }

    private News.Result run(DigestStore store, BatchProcessor.Source source, BatchProcessor.Analyzer analyzer, boolean recovery) {
        return new BatchProcessor(store, source, analyzer, item -> News.Delivery.delivered(42), recovery, 5).run();
    }

    private String logs() {
        return String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    private void assertLog(String expected) {
        assertTrue(logs().contains(expected), () -> "Missing log: " + expected + "\n" + logs());
    }
}
