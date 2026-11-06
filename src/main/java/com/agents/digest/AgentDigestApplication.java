package com.agents.digest;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(DigestProperties.class)
public class AgentDigestApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AgentDigestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ignored = application.run(args)) {
            // One batch runs during startup; context closure terminates the process.
        }
    }
    @Bean
    ApplicationRunner digestRunner(DigestProperties config) {
        return args -> {
            try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds())).followRedirects(HttpClient.Redirect.NORMAL).build();
                 DigestStore store = new MongoDigestStore(config)) {
                FeedSource sources = new FeedSource(config, http);
                ClaudeAnalyzer analyzer = new ClaudeAnalyzer(config, http, URI.create("https://api.anthropic.com/v1/messages"));
                URI telegram = config.offline() ? URI.create("https://api.telegram.org/") : URI.create("https://api.telegram.org/bot" + config.telegramToken() + "/sendMessage");
                new DigestBatch(config, store, sources::fetch, analyzer::analyze, new TelegramDelivery(config, http, telegram)).run(Instant.now());
                System.out.println("Digest batch completed successfully.");
            }
        };
    }
}
