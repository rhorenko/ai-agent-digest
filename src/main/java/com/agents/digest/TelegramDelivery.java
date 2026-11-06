package com.agents.digest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.bson.Document;

final class TelegramDelivery implements Delivery {
    private final DigestProperties config;
    private final HttpClient http;
    private final URI endpoint;
    TelegramDelivery(DigestProperties config, HttpClient http, URI endpoint) { this.config = config; this.http = http; this.endpoint = endpoint; }
    public Result send(DigestStore.Item item) throws InterruptedException {
        String message = message(item);
        if (config.offline()) { System.out.println("OFFLINE MESSAGE:\n" + message); return new Result("SENT", "offline", null); }
        String body = new Document("chat_id", config.telegramChatId()).append("text", message).append("disable_web_page_preview", true).toJson();
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(config.timeoutSeconds())).header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        for (int attempt = 0; attempt <= config.telegramRetries(); attempt++) {
            try {
                var response = HttpTransport.send(http, request, 100_000, config.timeoutSeconds());
                String responseBody = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                if (response.statusCode() == 200) {
                    try {
                        Document data = Document.parse(responseBody);
                        Document result = data.get("result", Document.class);
                        Object id = result == null ? null : result.get("message_id");
                        if (Boolean.TRUE.equals(data.getBoolean("ok")) && id instanceof Number) return new Result("SENT", id.toString(), null);
                    } catch (RuntimeException ignored) { }
                    return new Result("AMBIGUOUS", null, "Telegram response was incomplete");
                }
                if (response.statusCode() == 429) {
                    long wait = retryAfter(responseBody);
                    if (attempt < config.telegramRetries() && wait > 0 && wait <= 60) { Thread.sleep(wait * 1000); continue; }
                    return wait > 0 ? new Result("FAILED", null, "Telegram rate limit", wait) : new Result("AMBIGUOUS", null, "Telegram rate limit without valid retry_after");
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) return new Result("FAILED", null, "Telegram HTTP " + response.statusCode());
                return new Result("AMBIGUOUS", null, "Telegram HTTP " + response.statusCode());
            } catch (IOException e) {
                return new Result("AMBIGUOUS", null, "Telegram network failure; outcome unknown");
            }
        }
        return new Result("FAILED", null, "Telegram rate limit");
    }
    private static long retryAfter(String body) {
        try {
            Document params = Document.parse(body).get("parameters", Document.class);
            Object seconds = params == null ? null : params.get("retry_after");
            return seconds instanceof Number n ? n.longValue() : -1;
        } catch (RuntimeException e) { return -1; }
    }
    static String message(DigestStore.Item i) {
        return clip(i.title(), 350) + " (" + i.score() + "/10)\n\n" + clip(i.summary(), 700) + "\n\nWhy it matters: " + clip(i.relevance(), 500)
                + "\n\nPractical takeaway: " + clip(i.takeaway(), 500) + "\n\nSource: " + clip(i.source(), 150) + "\n" + i.url();
    }
    private static String clip(String value, int max) {
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
        if (cleaned.length() <= max) return cleaned;
        int end = max - 1;
        if (Character.isHighSurrogate(cleaned.charAt(end - 1))) end--;
        return cleaned.substring(0, end) + "…";
    }
}


