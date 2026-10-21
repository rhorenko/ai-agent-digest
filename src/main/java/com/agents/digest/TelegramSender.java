package com.agents.digest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agents.digest.News.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Map;

public final class TelegramSender {
    private static final Logger log = LoggerFactory.getLogger(TelegramSender.class);
    @FunctionalInterface public interface Sleeper { void sleep(Duration duration) throws InterruptedException; }
    private final HttpTransport http;
    private final DigestProperties config;
    private final Clock clock;
    private final Sleeper sleeper;

    public TelegramSender(HttpTransport http, DigestProperties config, Clock clock, Sleeper sleeper) {
        this.http = http;
        this.config = config;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public Delivery send(Item item) {
        Delivery delivery = sendMessage(item);
        // These categories are generated locally below; never log remote descriptions.
        if (delivery.state() != DeliveryState.DELIVERED) {
            log.warn("Telegram delivery failed: state={} reason={}", delivery.state(), delivery.error());
        }
        return delivery;
    }

    private Delivery sendMessage(Item item) {
        try {
            String message = MessageFormatter.format(item);
            byte[] body = AnalysisValidator.JSON.writeValueAsBytes(Map.of("chat_id", config.telegram().chatId(),
                    "text", message, "link_preview_options", Map.of("is_disabled", true)));
            URI endpoint = URI.create(config.telegram().endpoint().toString() + "/bot"
                    + config.telegram().botToken() + "/sendMessage");
            log.info("Telegram pacing: delayMs={}", config.telegram().interval().toMillis());
            sleeper.sleep(config.telegram().interval());
            for (int attempt = 0; ; attempt++) {
                long start = System.nanoTime();
                log.info("Telegram request started: attempt={}/{}", attempt + 1, config.telegram().retries() + 1);
                var response = http.send(HttpRequest.newBuilder(endpoint).timeout(config.timeout())
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
                log.info("Telegram response received: status={} elapsedMs={}", response.statusCode(),
                        (System.nanoTime() - start) / 1_000_000);
                JsonNode result;
                try { result = AnalysisValidator.JSON.readTree(response.body()); }
                catch (Exception e) { return Delivery.failed(DeliveryState.UNKNOWN, "TELEGRAM_INVALID_RESPONSE"); }
                int code = response.statusCode();
                boolean rejected = result != null && result.path("ok").isBoolean() && !result.get("ok").booleanValue();
                if (code == 429 && rejected && result.path("error_code").asInt() == 429) {
                    JsonNode seconds = result.path("parameters").path("retry_after");
                    if (!seconds.isIntegralNumber() || !seconds.canConvertToLong() || seconds.longValue() < 0
                            || seconds.longValue() > 86400) {
                        return Delivery.failed(DeliveryState.REJECTED, "INVALID_RETRY_AFTER");
                    }
                    Duration delay = Duration.ofSeconds(Math.max(1, seconds.longValue()));
                    if (attempt < config.telegram().retries() && delay.compareTo(config.telegram().maxRetryAfter()) <= 0) {
                        log.warn("Telegram retry scheduled: delaySeconds={}", delay.toSeconds());
                        sleeper.sleep(delay);
                        continue;
                    }
                    return new Delivery(DeliveryState.RETRY, null, "TELEGRAM_RATE_LIMIT",
                            clock.instant().plus(delay));
                }
                if (code >= 400 && code < 500 && rejected) {
                    return Delivery.failed(DeliveryState.REJECTED, "TELEGRAM_HTTP_" + code);
                }
                JsonNode messageId = result == null ? null : result.path("result").path("message_id");
                if (code == 200 && result.path("ok").isBoolean() && result.get("ok").booleanValue()
                        && messageId != null && messageId.isIntegralNumber() && messageId.canConvertToLong()
                        && messageId.longValue() > 0) {
                    return Delivery.delivered(messageId.longValue());
                }
                // A server error may occur after Telegram accepted the send.
                return Delivery.failed(DeliveryState.UNKNOWN, "TELEGRAM_UNCERTAIN_RESPONSE");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Delivery.failed(DeliveryState.UNKNOWN, "TELEGRAM_INTERRUPTED");
        } catch (IllegalArgumentException e) {
            return Delivery.failed(DeliveryState.REJECTED, "TELEGRAM_INVALID_MESSAGE");
        } catch (Exception e) {
            // Never propagate exception text: Telegram request URLs contain bot tokens.
            return Delivery.failed(DeliveryState.UNKNOWN, "TELEGRAM_TRANSPORT");
        }
    }
}
