package com.agents.digest;

import com.agents.digest.news.Analysis;
import com.agents.digest.news.Candidate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

public final class ClaudeAnalyzer {
    private final DigestProperties config;
    private final HttpClient http;
    private final URI endpoint;
    public ClaudeAnalyzer(DigestProperties config, HttpClient http, URI endpoint) { this.config = config; this.http = http; this.endpoint = endpoint; }

    public List<Analysis> analyze(List<Candidate> candidates) throws Exception {
        if (candidates.isEmpty()) return List.of();
        String result = config.offline() ? Resources.read("classpath:demo/analysis.json", 64_000) : request(candidates);
        Document response = Document.parse(result);
        if (!(response.get("items") instanceof List<?> items) || response.size() != 1) throw new IllegalArgumentException("Invalid structured analysis");
        List<Analysis> analyses = new ArrayList<>();
        for (Object value : items) {
            if (!(value instanceof Document d) || d.size() != 6 || !(d.get("importance") instanceof Integer score)) throw new IllegalArgumentException("Invalid analysis item");
            analyses.add(new Analysis(text(d, "candidateId"), score, text(d, "summary"), text(d, "relevance"), text(d, "takeaway"), text(d, "scoreExplanation")));
        }
        return analyses;
    }
    private String request(List<Candidate> candidates) throws Exception {
        String prompt = Resources.read("classpath:prompts/analysis-v1.txt", 10_000);
        String profile = Resources.read(config.profile(), 10_000);
        Document schema = Document.parse(Resources.read("classpath:prompts/analysis-schema-v1.json", 20_000));
        var data = candidates.stream().map(c -> new Document("id", c.id()).append("title", c.title()).append("source", c.source())
                .append("publishedAt", c.publishedAt().toString()).append("excerpt", c.excerpt())).toList();
        String content = new Document("profile", profile).append("maximumItems", config.maxItems()).append("candidates", data).toJson();
        Document body = new Document("model", config.model()).append("max_tokens", config.maxOutputTokens()).append("system", prompt)
                .append("messages", List.of(new Document("role", "user").append("content", content)))
                .append("output_config", new Document("format", new Document("type", "json_schema").append("schema", schema)));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("content-type", "application/json").header("anthropic-version", "2023-06-01").header("x-api-key", config.anthropicKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toJson())).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpResponse<byte[]> response = HttpTransport.send(http, request, 100_000, config.timeoutSeconds());
            String responseBody = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            int status = response.statusCode();
            if (status == 200) {
                Document envelope = Document.parse(responseBody);
                if (!"end_turn".equals(envelope.getString("stop_reason")) || !(envelope.get("content") instanceof List<?> blocks) || blocks.size() != 1 ||
                        !(blocks.getFirst() instanceof Document block) || !"text".equals(block.getString("type"))) throw new IllegalArgumentException("Incomplete Claude response");
                return text(block, "text");
            }
            if (!(status == 429 || status == 500 || status == 502 || status == 503 || status == 529) || attempt == 2) throw new IllegalStateException("Claude request failed with HTTP " + status);
            Thread.sleep(1000L * (attempt + 1));
        }
        throw new IllegalStateException("Claude request failed");
    }
    private static String text(Document doc, String key) {
        Object value = doc.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > 4000) throw new IllegalArgumentException("Invalid analysis field: " + key);
        return text;
    }
}


