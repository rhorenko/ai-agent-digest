package com.agents.digest;

import com.agents.digest.news.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DigestBatchTest {
    static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    static DigestProperties config() { return new DigestProperties(true, "mongodb://localhost", "test_db", "", "model", "", "", List.of(), "classpath:prompts/profile-v1.txt", 24, 5, 30, 1, 512, 2); }
    static Candidate candidate(String id) { return new Candidate(id, "Title", "Source", id, NOW.minusSeconds(60), NOW, "metadata"); }
    static Analysis analysis(String id, int score) { return new Analysis(id, score, "summary", "relevance", "action", "reason"); }
    static final class MemoryStore implements DigestStore {
        record Entry(Item item, String state) { }
        Map<String, Entry> entries = new LinkedHashMap<>();
        boolean locked, failInsert;
        public void lock() { if (locked) throw new IllegalStateException("locked"); locked = true; }
        public void unlock() { locked = false; }
        public boolean contains(String url) { return entries.containsKey(url); }
        public void insert(AnalysisValidator.Selected s) {
            if (failInsert) throw new IllegalStateException("database unavailable");
            var c=s.candidate(); var a=s.analysis();
            if (entries.containsKey(c.url())) throw new IllegalStateException("duplicate");
            entries.put(c.url(), new Entry(new Item(c.url(),c.title(),c.source(),a.importance(),a.summary(),a.relevance(),a.takeaway()), "PENDING"));
        }
        public List<Item> pending() { return entries.values().stream().filter(e -> e.state.equals("PENDING") || e.state.equals("FAILED")).map(Entry::item).sorted(Comparator.comparingInt(Item::score).reversed()).toList(); }
        public boolean hasUncertain() { return entries.values().stream().anyMatch(e -> e.state.equals("SENDING") || e.state.equals("AMBIGUOUS")); }
        public void sending(String url) { Entry e=entries.get(url); entries.put(url, new Entry(e.item, "SENDING")); }
        public void finish(String url, String status, String messageId, String error, long retryDelaySeconds) { Entry e=entries.get(url); entries.put(url, new Entry(e.item, status)); }
        public void close() { }
    }
    @Test void successfulRunOrdersAndRerunDoesNotAnalyzeOrResend() throws Exception {
        var store = new MemoryStore(); var sent = new ArrayList<String>(); int[] calls = {0};
        var a=candidate("https://example.org/a"); var b=candidate("https://example.org/b");
        var batch = new DigestBatch(config(),store, now -> List.of(a,b), candidates -> {calls[0]++; return List.of(analysis(a.id(), 3), analysis(b.id(), 9));}, item -> {sent.add(item.url()); return new Delivery.Result("SENT", "1", null);});
        batch.run(NOW); batch.run(NOW);
        assertEquals(List.of(b.url(),a.url()), sent); assertEquals(1,calls[0]); assertFalse(store.locked);
    }
    @Test void emptyOrIrrelevantSelectionSendsNothing() throws Exception {
        var store = new MemoryStore(); var sent = new ArrayList<String>();
        var batch = new DigestBatch(config(),store, now -> List.of(candidate("https://example.org/a")), candidates -> List.of(), item -> {sent.add(item.url()); return null;});
        batch.run(NOW); assertTrue(store.entries.isEmpty()); assertTrue(sent.isEmpty());
    }
    @Test void rejectsInvalidResponseAndPersistenceFailureBeforeSending() {
        var store = new MemoryStore(); int[] sends={0}; var c=candidate("https://example.org/a");
        Delivery delivery = item -> {sends[0]++; return new Delivery.Result("SENT", "1", null);};
        var invalid = new DigestBatch(config(),store, n -> List.of(c), cs -> List.of(analysis("https://invented.invalid/", 8)),delivery);
        assertThrows(IllegalArgumentException.class, () -> invalid.run(NOW)); assertTrue(store.entries.isEmpty());
        store.failInsert = true;
        var databaseFailure = new DigestBatch(config(),store,n -> List.of(c), cs -> List.of(analysis(c.id(), 8)),delivery);
        assertThrows(IllegalStateException.class, () -> databaseFailure.run(NOW)); assertEquals(0,sends[0]); assertFalse(store.locked);
    }
    @Test void partialFailureContinuesAndRetriesPersistedItemWithoutModel() throws Exception {
        var store = new MemoryStore(); var a=candidate("https://example.org/a"); var b=candidate("https://example.org/b"); var attempts=new ArrayList<String>();
        var first=new DigestBatch(config(),store,n -> List.of(a,b),cs -> List.of(analysis(a.id(), 9),analysis(b.id(), 8)), item -> { attempts.add(item.url()); return item.url().equals(a.url()) ? new Delivery.Result("FAILED",null,"rate") : new Delivery.Result("SENT","2",null); });
        assertThrows(IllegalStateException.class, () -> first.run(NOW)); assertEquals(List.of(a.url(),b.url()),attempts);
        var second=new DigestBatch(config(),store,n -> List.of(a,b),cs -> { fail("no second analysis"); return List.of(); }, item -> {attempts.add(item.url()); return new Delivery.Result("SENT","3",null);});
        second.run(NOW); assertEquals(List.of(a.url(),b.url(),a.url()),attempts);
    }
    @Test void ambiguousItemsNeverAutoRetryAndFailTheBatch() {
        var store=new MemoryStore(); var c=candidate("https://example.org/a"); int[] count={0};
        var batch=new DigestBatch(config(),store,n -> List.of(c),cs -> List.of(analysis(c.id(),5)),item -> {count[0]++; return new Delivery.Result("AMBIGUOUS",null,"timeout");});
        assertThrows(IllegalStateException.class, () -> batch.run(NOW)); assertThrows(IllegalStateException.class, () -> batch.run(NOW)); assertEquals(1,count[0]);
    }
    @Test void recoveredAndNewItemsShareImportanceOrder() throws Exception {
        var store = new MemoryStore(); var old = candidate("https://example.org/old"); var fresh = candidate("https://example.org/new");
        store.insert(new AnalysisValidator.Selected(old, analysis(old.id(), 3)));
        var sent = new ArrayList<String>();
        var batch = new DigestBatch(config(), store, n -> List.of(old, fresh), cs -> List.of(analysis(fresh.id(), 9)), i -> { sent.add(i.url()); return new Delivery.Result("SENT", "1", null); });
        batch.run(NOW); assertEquals(List.of(fresh.url(), old.url()), sent);
    }
    @Test void discoveryFailureStillRetriesPersistedItemAndReturnsFailure() {
        var store = new MemoryStore(); var old = candidate("https://example.org/old");
        store.insert(new AnalysisValidator.Selected(old, analysis(old.id(), 3)));
        var sent = new ArrayList<String>();
        var batch = new DigestBatch(config(), store, n -> { throw new IllegalStateException("feed unavailable"); }, cs -> { fail("must not call model"); return List.of(); }, i -> { sent.add(i.url()); return new Delivery.Result("SENT", "1", null); });
        assertThrows(IllegalStateException.class, () -> batch.run(NOW)); assertEquals(List.of(old.url()), sent);
    }
}


