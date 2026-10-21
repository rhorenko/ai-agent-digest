package com.agents.digest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agents.digest.News.*;
import java.util.*;

public final class BatchProcessor {
    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);
    @FunctionalInterface public interface Source { List<Candidate> retrieve() throws Exception; }
    @FunctionalInterface public interface Analyzer { List<Item> analyze(List<Candidate> candidates) throws Exception; }
    @FunctionalInterface public interface Sender { Delivery send(Item item); }

    private final DigestStore store;
    private final Source source;
    private final Analyzer analyzer;
    private final Sender sender;
    private final boolean recoveryOnly;
    private final int deliveryLimit;

    public BatchProcessor(DigestStore store, Source source, Analyzer analyzer, Sender sender,
                          boolean recoveryOnly, int deliveryLimit) {
        this.store = store;
        this.source = source;
        this.analyzer = analyzer;
        this.sender = sender;
        this.recoveryOnly = recoveryOnly;
        this.deliveryLimit = deliveryLimit;
    }

    public Result run() {
        long start = System.nanoTime();
        String stage = "database-initialize";
        log.info("Batch started: recoveryOnly={} deliveryLimit={}", recoveryOnly, deliveryLimit);
        String owner = UUID.randomUUID().toString();
        boolean acquired = false;
        boolean started = false;
        int delivered = 0;
        Result result = new Result(1, "FAILED", 0, 0);
        try {
            log.info("Database initialization started");
            store.initialize();
            log.info("Database initialization completed");
            stage = "lock-acquire";
            acquired = store.acquire(owner);
            if (!acquired) {
                log.warn("Batch skipped: another run holds the database lock (BUSY)");
                return new Result(3, "BUSY", 0, 0);
            }
            log.info("Database lock acquired");
            stage = "run-start";
            store.startRun(owner);
            started = true;
            stage = "recovery";
            log.info("Interrupted delivery recovery started");
            store.recoverInterrupted();
            log.info("Interrupted delivery recovery completed");
            if (!recoveryOnly) {
                stage = "source-retrieval";
                log.info("Source retrieval started");
                List<Candidate> retrieved = source.retrieve();
                stage = "deduplication";
                List<Candidate> fresh = retrieved.stream()
                        .filter(candidate -> !store.exists(candidate.url())).toList();
                log.info("Source retrieval/deduplication completed: retrieved={} alreadyStored={} fresh={}",
                        retrieved.size(), retrieved.size() - fresh.size(), fresh.size());
                if (fresh.isEmpty()) {
                    log.info("Analysis skipped: reason={}", retrieved.isEmpty() ? "NO_CANDIDATES" : "ALL_ALREADY_STORED");
                } else {
                    stage = "analysis-validation";
                    log.info("Analysis and validation started: candidates={}", fresh.size());
                    List<Item> selected = analyzer.analyze(fresh);
                    log.info("Analysis and validation completed: selected={}", selected.size());
                    if (selected.isEmpty()) log.info("No new items selected: reason=MODEL_SELECTED_ZERO");
                    stage = "persistence";
                    // Persist all items first: any database failure prevents delivery.
                    for (Item item : selected) store.save(item);
                    log.info("Selected items persisted: count={}", selected.size());
                }
            } else {
                log.info("Source retrieval and analysis skipped: reason=RECOVERY_ONLY");
            }
            stage = "pending-load";
            List<Pending> pendingItems = store.pending(deliveryLimit);
            log.info("Delivery queue loaded: pending={} limit={}", pendingItems.size(), deliveryLimit);
            if (pendingItems.isEmpty()) log.info("Delivery skipped: reason=NO_PENDING_ITEMS");
            int deliveryNumber = 0;
            for (Pending pending : pendingItems) {
                stage = "delivery-mark-sending";
                log.info("Delivery started: item={}/{}", ++deliveryNumber, pendingItems.size());
                store.markSending(pending.url());
                stage = "delivery-send";
                Delivery outcome = sender.send(pending.item());
                stage = "delivery-record";
                store.recordDelivery(pending.url(), outcome);
                if (outcome.state() == DeliveryState.DELIVERED) {
                    delivered++;
                    log.info("Delivery completed: item={}/{} state={}", deliveryNumber, pendingItems.size(), outcome.state());
                } else {
                    log.warn("Delivery completed: item={}/{} state={} (see stored delivery error category)",
                            deliveryNumber, pendingItems.size(), outcome.state());
                }
                if (Thread.currentThread().isInterrupted()) break;
            }
            stage = "run-finalize";
            int unresolved = Math.toIntExact(store.unresolved());
            result = new Result(unresolved == 0 ? 0 : 2,
                    unresolved == 0 ? "SUCCESS" : "PARTIAL", delivered, unresolved);
            store.finishRun(owner, result, null);
        } catch (Exception e) {
            result = new Result(1, "FAILED", delivered, 0);
            // Persist safe categories only. Exception messages may contain network credentials.
            if (started) {
                try { store.finishRun(owner, result, e instanceof IllegalArgumentException
                        ? "INVALID_EXTERNAL_DATA" : "OPERATION_FAILED"); }
                catch (Exception ignored) { log.error("Unable to persist run failure"); }
            }
            log.error("Batch failed: stage={} errorType={}", stage, e.getClass().getSimpleName());
        } finally {
            if (acquired) {
                try { store.release(owner); log.info("Database lock released"); }
                catch (Exception e) {
                    result = new Result(1, "LOCK_RELEASE_FAILED", delivered, result.unresolved());
                    try { store.finishRun(owner, result, "LOCK_RELEASE_FAILED"); }
                    catch (Exception ignored) { /* Database may be unavailable. */ }
                    log.error("Batch lock release failed; manual recovery required");
                }
            }
        }
        log.info("Batch completed: status={} delivered={} unresolved={} exit={} elapsedMs={}",
                result.status(), result.delivered(), result.unresolved(), result.exitCode(),
                (System.nanoTime() - start) / 1_000_000);
        return result;
    }
}
