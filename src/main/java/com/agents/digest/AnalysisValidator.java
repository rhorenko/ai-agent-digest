package com.agents.digest;

import com.agents.digest.News.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.JsonFactory;
import java.time.Instant;
import java.util.*;

public final class AnalysisValidator {
    static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Set<String> FIELDS = Set.of("candidateId", "score", "summary", "relevance", "takeaway", "scoreReason");

    public List<Item> validate(String json, List<Candidate> candidates, int maximum, Instant now) throws Exception {
        JsonNode root = JSON.readTree(json);
        require(root != null && root.isObject() && root.size() == 1 && root.has("items"), "root");
        JsonNode items = root.get("items");
        require(items.isArray() && items.size() <= maximum, "selection size");
        Map<String, Candidate> byId = new HashMap<>();
        for (Candidate candidate : candidates) byId.put(candidate.id(), candidate);
        Set<String> selected = new HashSet<>();
        List<Item> result = new ArrayList<>();
        for (JsonNode item : items) {
            require(item.isObject() && item.size() == FIELDS.size(), "analysis fields");
            item.fieldNames().forEachRemaining(name -> require(FIELDS.contains(name), "extra field"));
            String id = requiredText(item, "candidateId", 100, false);
            require(byId.containsKey(id) && selected.add(id), "candidate correspondence");
            EnglishText.require(byId.get(id).title());
            JsonNode score = item.get("score");
            require(score != null && score.isIntegralNumber() && score.canConvertToInt()
                    && score.intValue() >= 1 && score.intValue() <= 10, "importance score");
            Analysis analysis = new Analysis(id, score.intValue(),
                    requiredText(item, "summary", 650, true), requiredText(item, "relevance", 500, true),
                    requiredText(item, "takeaway", 500, true), requiredText(item, "scoreReason", 350, true));
            result.add(new Item(byId.get(id), analysis, now));
        }
        result.sort(Comparator.comparingInt((Item item) -> item.analysis().score()).reversed());
        return result;
    }

    private static String requiredText(JsonNode item, String field, int maxLength, boolean english) {
        JsonNode node = item.get(field);
        require(node != null && node.isTextual(), field);
        String value = node.textValue().trim();
        require(!value.isEmpty() && value.length() <= maxLength, field + " length");
        require(value.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n'), field + " controls");
        if (english) EnglishText.require(value);
        return value;
    }

    private static void require(boolean valid, String field) {
        if (!valid) throw new IllegalArgumentException("INVALID_ANALYSIS: " + field);
    }
}
