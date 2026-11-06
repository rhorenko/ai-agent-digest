package com.agents.digest.news;

import java.time.Instant;

/** Data obtained from a feed, not from the language model. */
public record Candidate(String id, String title, String source, String url, Instant publishedAt,
                        Instant discoveredAt, String excerpt) { }
