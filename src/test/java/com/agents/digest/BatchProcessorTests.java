package com.agents.digest;

import com.agents.digest.News.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchProcessorTests {
    @Test void persistsAllBeforeSendingAndOrdersDeliveryThroughStore() throws Exception {
        DigestStore store = mock(DigestStore.class);
        when(store.acquire(anyString())).thenReturn(true);
        var high = TestConfig.item("high", 9);
        var low = TestConfig.item("low", 7);
        when(store.pending(5)).thenReturn(List.of(new Pending(high.candidate().url(), high), new Pending(low.candidate().url(), low)));
        BatchProcessor.Sender sender = mock(BatchProcessor.Sender.class);
        when(sender.send(any())).thenReturn(Delivery.delivered(42));
        var result = new BatchProcessor(store, () -> List.of(high.candidate(), low.candidate()),
                candidates -> List.of(high, low), sender, false, 5).run();
        assertEquals(0, result.exitCode());
        assertEquals(2, result.delivered());
        var order = inOrder(store, sender);
        order.verify(store).save(high);
        order.verify(store).save(low);
        order.verify(store).markSending(high.candidate().url());
        order.verify(sender).send(high);
        order.verify(store).recordDelivery(eq(high.candidate().url()), any());
        order.verify(store).markSending(low.candidate().url());
        order.verify(sender).send(low);
        verify(store).release(anyString());
    }

    @Test void databaseFailureBeforeDeliveryNeverSends() {
        DigestStore store = readyStore();
        var item = TestConfig.item("one", 8);
        doThrow(new IllegalStateException("db failed")).when(store).save(item);
        BatchProcessor.Sender sender = mock(BatchProcessor.Sender.class);
        var result = new BatchProcessor(store, () -> List.of(item.candidate()), ignored -> List.of(item), sender, false, 5).run();
        assertEquals(1, result.exitCode());
        verifyNoInteractions(sender);
        verify(store).release(anyString());
    }

    @Test void invalidClaudeSelectionFailsBeforeSaveAndSend() {
        DigestStore store = readyStore();
        BatchProcessor.Sender sender = mock(BatchProcessor.Sender.class);
        var result = new BatchProcessor(store, () -> List.of(TestConfig.candidate("one")),
                items -> new AnalysisValidator().validate("{\"items\":[{}]}", items, 5, TestConfig.CLOCK.instant()),
                sender, false, 5).run();
        assertEquals(1, result.exitCode());
        verify(store, never()).save(any());
        verifyNoInteractions(sender);
    }

    @Test void emptySelectionIsSuccessfulAndSkipsAnalysisIfNoNewSources() {
        DigestStore store = readyStore();
        when(store.pending(5)).thenReturn(List.of());
        BatchProcessor.Analyzer analyzer = mock(BatchProcessor.Analyzer.class);
        var result = new BatchProcessor(store, List::of, analyzer, item -> Delivery.delivered(1), false, 5).run();
        assertEquals(0, result.exitCode());
        verifyNoInteractions(analyzer);
    }

    @Test void partialFailureContinuesAndRecoveryBypassesSourcesAndClaude() {
        DigestStore store = readyStore();
        var high = TestConfig.item("high", 9);
        var low = TestConfig.item("low", 7);
        when(store.pending(5)).thenReturn(List.of(new Pending(high.candidate().url(), high), new Pending(low.candidate().url(), low)));
        when(store.unresolved()).thenReturn(1L);
        BatchProcessor.Source source = mock(BatchProcessor.Source.class);
        BatchProcessor.Analyzer analyzer = mock(BatchProcessor.Analyzer.class);
        BatchProcessor.Sender sender = mock(BatchProcessor.Sender.class);
        when(sender.send(high)).thenReturn(Delivery.failed(DeliveryState.REJECTED, "HTTP_400"));
        when(sender.send(low)).thenReturn(Delivery.delivered(8));
        var result = new BatchProcessor(store, source, analyzer, sender, true, 5).run();
        assertEquals(2, result.exitCode());
        assertEquals(1, result.delivered());
        verify(sender).send(low);
        verify(store).recordDelivery(eq(high.candidate().url()), argThat(d -> d.state() == DeliveryState.REJECTED));
        verifyNoInteractions(source, analyzer);
    }

    @Test void overlappingRunExitsBusyWithoutProcessing() {
        DigestStore store = mock(DigestStore.class);
        BatchProcessor.Source source = mock(BatchProcessor.Source.class);
        assertEquals(3, new BatchProcessor(store, source, null, null, false, 5).run().exitCode());
        verifyNoInteractions(source);
        verify(store, never()).release(anyString());
    }

    private DigestStore readyStore() {
        DigestStore store = mock(DigestStore.class);
        when(store.acquire(anyString())).thenReturn(true);
        return store;
    }
}
