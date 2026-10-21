package com.agents.digest;

import com.agents.digest.News.*;
import java.util.List;

public interface DigestStore {
    void initialize();
    boolean acquire(String owner);
    void release(String owner);
    void startRun(String owner);
    void finishRun(String owner, Result result, String error);
    void recoverInterrupted();
    boolean exists(String normalizedUrl);
    void save(Item item);
    List<Pending> pending(int limit);
    void markSending(String url);
    void recordDelivery(String url, Delivery delivery);
    long unresolved();
}
