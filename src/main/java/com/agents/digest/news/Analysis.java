package com.agents.digest.news;

/** Structured model output refers to a source only by the locally assigned ID. */
public record Analysis(String candidateId, int importance, String summary, String relevance,
                       String takeaway, String scoreExplanation) { }
