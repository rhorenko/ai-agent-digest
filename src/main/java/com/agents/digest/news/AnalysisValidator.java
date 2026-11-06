package com.agents.digest.news;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnalysisValidator {
    public record Selected(Candidate candidate, Analysis analysis) { }

    public List<Selected> validate(List<Candidate> candidates, List<Analysis> analyses,
                                   int maximum, Instant earliest, Instant now) {
        if (candidates == null || analyses == null || maximum < 1 || earliest == null || now == null || earliest.isAfter(now)) {
            throw new IllegalArgumentException("Invalid analysis input");
        }
        if (analyses.size() > maximum) throw new IllegalArgumentException("Too many selections");
        Map<String, Candidate> byId = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate == null || blank(candidate.id()) || byId.putIfAbsent(candidate.id(), candidate) != null) {
                throw new IllegalArgumentException("Invalid or duplicate candidate ID");
            }
        }
        List<Selected> selected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> urls = new HashSet<>();
        for (Analysis analysis : analyses) {
            if (analysis == null || blank(analysis.candidateId()) || !ids.add(analysis.candidateId())) {
                throw new IllegalArgumentException("Invalid or duplicate selection");
            }
            Candidate candidate = byId.get(analysis.candidateId());
            if (candidate == null || blank(candidate.title()) || blank(candidate.source()) || candidate.publishedAt() == null ||
                    candidate.discoveredAt() == null || candidate.publishedAt().isBefore(earliest) || candidate.publishedAt().isAfter(now)) {
                throw new IllegalArgumentException("Selection does not match an eligible source");
            }
            String url = NewsUrl.normalize(candidate.url());
            if (!urls.add(url)) throw new IllegalArgumentException("Duplicate source URL");
            if (analysis.importance() < 1 || analysis.importance() > 10 || blank(analysis.summary()) ||
                    blank(analysis.relevance()) || blank(analysis.takeaway()) || blank(analysis.scoreExplanation())) {
                throw new IllegalArgumentException("Invalid analysis fields");
            }
            selected.add(new Selected(candidate, analysis));
        }
        selected.sort((a, b) -> Integer.compare(b.analysis().importance(), a.analysis().importance()));
        return List.copyOf(selected);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
