package com.agents.digest;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "digest")
public record DigestProperties(boolean offline, String mongoUri, String mongoDatabase, String anthropicKey,
        String model, String telegramToken, String telegramChatId, List<String> feeds, String profile,
        int windowHours, int maxItems, int maxCandidates, int timeoutSeconds, int maxOutputTokens, int telegramRetries) {
    public DigestProperties {
        required(mongoUri, "mongo-uri");
        required(mongoDatabase, "mongo-database");
        required(profile, "profile");
        if (!mongoDatabase.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid MongoDB database name");
        if (windowHours < 1 || windowHours > 720 || maxItems < 1 || maxItems > 20 || maxCandidates < maxItems || maxCandidates > 100 ||
                timeoutSeconds < 1 || timeoutSeconds > 120 || maxOutputTokens < 128 || maxOutputTokens > 8192 || telegramRetries < 0 || telegramRetries > 5) {
            throw new IllegalArgumentException("Invalid digest limits");
        }
        if (!offline) {
            required(anthropicKey, "anthropic-key"); required(model, "model");
            required(telegramToken, "telegram-token"); required(telegramChatId, "telegram-chat-id");
            if (feeds == null || feeds.isEmpty() || feeds.size() > 20) throw new IllegalArgumentException("Configure 1-20 feeds");
            for (String feed : feeds) {
                URI uri = URI.create(feed);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("Feeds must be HTTPS URLs");
            }
        }
        feeds = feeds == null ? List.of() : List.copyOf(feeds);
    }
    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing digest configuration: " + name);
    }
}
