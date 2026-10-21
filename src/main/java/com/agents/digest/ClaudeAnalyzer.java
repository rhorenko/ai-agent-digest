package com.agents.digest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agents.digest.News.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ClaudeAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ClaudeAnalyzer.class);
    private final HttpTransport http;
    private final DigestProperties config;
    private final String prompt;
    private final JsonNode schema;

    public ClaudeAnalyzer(HttpTransport http, DigestProperties config) throws Exception {
        this.http = http;
        this.config = config;
        prompt = resource("/prompts/analysis-v1.txt");
        schema = AnalysisValidator.JSON.readTree(resource("/prompts/analysis-v1.schema.json"));
    }

    public List<Candidate> boundContext(List<Candidate> candidates) throws Exception {
        List<Candidate> bounded = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (bounded.size() >= config.maxCandidates()) break;
            bounded.add(candidate);
            if (contextSize(bounded) > config.maxContextChars()) {
                bounded.removeLast();
                break;
            }
        }
        if (bounded.isEmpty() && !candidates.isEmpty()) {
            throw new IllegalArgumentException("CONTEXT_LIMIT_TOO_SMALL");
        }
        log.info("Claude context bounded: input={} included={} excluded={} contextChars={} limit={}",
                candidates.size(), bounded.size(), candidates.size() - bounded.size(), contextSize(bounded), config.maxContextChars());
        return bounded;
    }

    public String analyze(List<Candidate> candidates) throws Exception {
        if (contextSize(candidates) > config.maxContextChars() || candidates.size() > config.maxCandidates()) {
            throw new IllegalArgumentException("CLAUDE_CONTEXT_LIMIT");
        }
        Map<String, Object> outputConfig = new LinkedHashMap<>();
        outputConfig.put("format", Map.of("type", "json_schema", "schema", schema));
        String effort = config.claude().effort();
        if (effort != null && !effort.isBlank()) outputConfig.put("effort", effort);
        Map<String, Object> request = Map.of(
                "model", config.claude().model(), "max_tokens", config.claude().maxTokens(),
                "system", prompt,
                "messages", List.of(Map.of("role", "user", "content", userContent(candidates))),
                "output_config", outputConfig);
        byte[] body = AnalysisValidator.JSON.writeValueAsBytes(request);
        for (int attempt = 0; ; attempt++) {
            long start = System.nanoTime();
            log.info("Claude request started: model={} candidates={} attempt={}/{} timeout={}",
                    config.claude().model(), candidates.size(), attempt + 1, config.claude().retries() + 1, config.claude().timeout());
            var response = http.send(HttpRequest.newBuilder(config.claude().endpoint()).timeout(config.claude().timeout())
                    .header("x-api-key", config.claude().apiKey()).header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
            int code = response.statusCode();
            log.info("Claude response received: status={} elapsedMs={}", code, (System.nanoTime() - start) / 1_000_000);
            if ((code == 429 || code >= 500) && attempt < config.claude().retries()) {
                long wait = retryDelay(response, attempt);
                if (wait > 30) {
                    log.warn("Claude retry deferred: delay exceeds 30 seconds or Retry-After is unsupported");
                    throw new IllegalStateException("CLAUDE_RETRY_DEFERRED");
                }
                log.warn("Claude retry scheduled: status={} delaySeconds={}", code, wait);
                Thread.sleep(wait * 1000);
                continue;
            }
            if (code != 200) throw new IllegalStateException("CLAUDE_HTTP_" + code);
            JsonNode result = AnalysisValidator.JSON.readTree(response.body());
            if (!"end_turn".equals(result.path("stop_reason").asText())) {
                log.warn("Claude analysis rejected: reason=INCOMPLETE_OR_REFUSED");
                throw new IllegalArgumentException("CLAUDE_INCOMPLETE_OR_REFUSED");
            }
            return selectionText(result.path("content"));
        }
    }

    // Current models think by default; thinking blocks precede the single structured text block.
    private static String selectionText(JsonNode content) {
        if (!content.isArray()) throw new IllegalArgumentException("CLAUDE_INVALID_CONTENT");
        List<JsonNode> texts = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asText();
            if ("text".equals(type)) texts.add(block);
            else if (!"thinking".equals(type) && !"redacted_thinking".equals(type)) {
                throw new IllegalArgumentException("CLAUDE_INVALID_CONTENT");
            }
        }
        if (texts.size() != 1 || !texts.getFirst().path("text").isTextual()) {
            throw new IllegalArgumentException("CLAUDE_INVALID_CONTENT");
        }
        return texts.getFirst().get("text").textValue();
    }

    private String userContent(List<Candidate> candidates) throws Exception {
        return AnalysisValidator.JSON.writeValueAsString(Map.of("profile", config.profile(),
                "maximumItems", config.maxItems(), "candidates", candidates));
    }

    private int contextSize(List<Candidate> candidates) throws Exception {
        return prompt.length() + schema.toString().length() + userContent(candidates).length();
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("retry-after").orElse("");
        try { return Math.max(1L << attempt, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return value.isEmpty() ? 1L << attempt : 31; }
    }

    private static String resource(String path) throws Exception {
        try (var input = ClaudeAnalyzer.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing prompt resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
