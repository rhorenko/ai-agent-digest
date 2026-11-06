package com.agents.digest;

import com.agents.digest.news.AnalysisValidator.Selected;
import java.util.List;

interface DigestStore extends AutoCloseable {
    record Item(String url, String title, String source, int score, String summary, String relevance, String takeaway) { }
    void lock();
    void unlock();
    boolean contains(String url);
    void insert(Selected item);
    List<Item> pending();
    boolean hasUncertain();
    void sending(String url);
    void finish(String url, String state, String messageId, String error, long retryDelaySeconds);
    void close();
}


