package com.agents.digest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("digest")
public record DigestProperties(
        boolean offline, boolean recoveryOnly, String profile, Duration window,
        int maxItems, int maxCandidates, int excerptChars, int maxContextChars,
        Duration timeout, int maxResponseBytes, List<URI> feeds,
        String mongoUri, String database, Claude claude, Telegram telegram) {

    public record Claude(URI endpoint, String apiKey, String model, int maxTokens, int retries,
                         String effort, Duration timeout) {
        @Override public String toString() { return "Claude[redacted]"; }
    }
    public record Telegram(URI endpoint, String botToken, String chatId, int retries,
                           Duration maxRetryAfter, Duration interval) {
        @Override public String toString() { return "Telegram[redacted]"; }
    }

    public DigestProperties {
        require(profile != null && !profile.isBlank() && profile.length() <= 4000, "profile");
        require(window != null && !window.isNegative() && !window.isZero()
                && window.compareTo(Duration.ofDays(30)) <= 0, "window (0РІР‚вЂњ30 days)");
        require(maxItems >= 1 && maxItems <= 20, "max-items (1РІР‚вЂњ20)");
        require(maxCandidates >= maxItems && maxCandidates <= 100, "max-candidates");
        require(excerptChars >= 100 && excerptChars <= 4000, "excerpt-chars");
        require(maxContextChars >= 1000 && maxContextChars <= 100000, "max-context-chars");
        require(timeout != null && timeout.compareTo(Duration.ofSeconds(1)) >= 0
                && timeout.compareTo(Duration.ofMinutes(3)) <= 0, "timeout (1sРІР‚вЂњ3m)");
        require(maxResponseBytes >= 1024 && maxResponseBytes <= 5_000_000, "max-response-bytes");
        require(mongoUri != null && mongoUri.startsWith("mongodb"), "mongo-uri");
        require(database != null && database.matches("[A-Za-z0-9_-]{1,60}"), "database");
        require(!offline || database.endsWith("_offline") || database.endsWith("_test"), "offline database suffix");
        require(claude != null && telegram != null, "integration settings");
        require(claude.maxTokens() >= 256 && claude.maxTokens() <= 16000, "claude.max-tokens");
        require(claude.retries() >= 0 && claude.retries() <= 3, "claude.retries");
        // Blank effort omits the parameter for models that do not support it.
        require(claude.effort() == null || claude.effort().isBlank()
                || List.of("low", "medium", "high", "xhigh", "max").contains(claude.effort()), "claude.effort");
        require(claude.timeout() != null && claude.timeout().compareTo(Duration.ofSeconds(1)) >= 0
                && claude.timeout().compareTo(Duration.ofMinutes(5)) <= 0, "claude.timeout (1sРІР‚вЂњ5m)");
        require(telegram.retries() >= 0 && telegram.retries() <= 3, "telegram.retries");
        require(telegram.maxRetryAfter() != null && !telegram.maxRetryAfter().isNegative()
                && telegram.maxRetryAfter().compareTo(Duration.ofSeconds(60)) <= 0, "telegram.max-retry-after");
        require(telegram.interval() != null && !telegram.interval().isNegative()
                && telegram.interval().compareTo(Duration.ofSeconds(10)) <= 0, "telegram.interval");
        require(claude.endpoint() != null && telegram.endpoint() != null, "endpoints");
        if (!offline) {
            require(telegram.botToken() != null && !telegram.botToken().isBlank(), "telegram.bot-token");
            require(telegram.chatId() != null && !telegram.chatId().isBlank(), "telegram.chat-id");
            require("https".equals(telegram.endpoint().getScheme()), "telegram HTTPS endpoint");
            if (!recoveryOnly) {
                require(claude.apiKey() != null && !claude.apiKey().isBlank(), "claude.api-key");
                require(claude.model() != null && !claude.model().isBlank(), "claude.model");
                require("https".equals(claude.endpoint().getScheme()), "claude HTTPS endpoint");
                require(feeds != null && !feeds.isEmpty() && feeds.size() <= 30, "1-30 feeds");
                feeds.forEach(uri -> require("https".equals(uri.getScheme()), "feed HTTPS URL"));
            }
        }
    }

    private static void require(boolean valid, String setting) {
        if (!valid) throw new IllegalArgumentException("Invalid digest setting: " + setting);
    }

    // Do not use the generated record toString: nested configuration contains credentials.
    @Override public String toString() { return "DigestProperties[redacted]"; }
}
