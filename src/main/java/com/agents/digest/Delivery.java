package com.agents.digest;

interface Delivery {
    record Result(String state, String messageId, String error, long retryDelaySeconds) {
        Result(String state, String messageId, String error) { this(state, messageId, error, 0); }
    }
    Result send(DigestStore.Item item) throws InterruptedException;
}

