package com.agents.digest;

import com.agents.digest.news.Analysis;
import com.agents.digest.news.AnalysisValidator;
import com.agents.digest.news.Candidate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class DigestBatch {
    @FunctionalInterface interface Sources { List<Candidate> fetch(Instant now) throws Exception; }
    @FunctionalInterface interface Analyzer { List<Analysis> analyze(List<Candidate> candidates) throws Exception; }
    private final DigestProperties config;
    private final DigestStore store;
    private final Sources sources;
    private final Analyzer analyzer;
    private final Delivery delivery;
    DigestBatch(DigestProperties config, DigestStore store, Sources sources, Analyzer analyzer, Delivery delivery) {
        this.config = config; this.store = store; this.sources = sources; this.analyzer = analyzer; this.delivery = delivery;
    }
    void run(Instant now) throws Exception {
        store.lock();
        try {
            Exception discoveryFailure = null;
            try {
                List<Candidate> fresh = new ArrayList<>();
                for (Candidate candidate : sources.fetch(now)) if (!store.contains(candidate.url())) fresh.add(candidate);
                if (!fresh.isEmpty()) {
                    var selections = new AnalysisValidator().validate(fresh, analyzer.analyze(fresh), config.maxItems(), now.minus(Duration.ofHours(config.windowHours())), now);
                    // Validate the entire response before writing any model-generated content.
                    for (var item : selections) store.insert(item);
                }
            } catch (Exception e) {
                // Previously persisted items remain deliverable even when discovery or analysis fails.
                discoveryFailure = e;
            }
            boolean failed = deliverPending();
            failed |= store.hasUncertain();
            if (discoveryFailure != null) throw discoveryFailure;
            if (failed) throw new IllegalStateException("One or more deliveries failed or have an unknown outcome");
        } finally {
            store.unlock();
        }
    }
    private boolean deliverPending() throws InterruptedException {
        boolean failed = false;
        for (DigestStore.Item item : store.pending()) {
            store.sending(item.url());
            Delivery.Result result = delivery.send(item);
            store.finish(item.url(), result.state(), result.messageId(), result.error(), result.retryDelaySeconds());
            if (!"SENT".equals(result.state())) failed = true;
        }
        return failed;
    }
}



