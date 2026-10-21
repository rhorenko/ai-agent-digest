package com.agents.digest;

import java.time.Instant;

public final class News {
    private News() {}

    public record Candidate(String id, String title, String source, String url,
                            Instant publishedAt, Instant discoveredAt, String excerpt, String material) {}
    public record Analysis(String candidateId, int score, String summary, String relevance,
                           String takeaway, String scoreReason) {}
    public record Item(Candidate candidate, Analysis analysis, Instant processedAt) {}
    public record Pending(String url, Item item) {}
    public enum DeliveryState { PENDING, SENDING, DELIVERED, RETRY, REJECTED, UNKNOWN }
    public record Delivery(DeliveryState state, Long messageId, String error, Instant retryAt) {
        public static Delivery delivered(long id) {
            return new Delivery(DeliveryState.DELIVERED, id, null, null);
        }
        public static Delivery failed(DeliveryState state, String code) {
            return new Delivery(state, null, code, null);
        }
    }
    public record Result(int exitCode, String status, int delivered, int unresolved) {}
}
