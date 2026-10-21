package com.agents.digest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import java.time.*;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableConfigurationProperties(DigestProperties.class)
public class AgentDigestApplication {
    private static final Logger log = LoggerFactory.getLogger(AgentDigestApplication.class);
    private int exitCode = 1;

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    static int execute(String... args) {
        SpringApplication application = new SpringApplication(AgentDigestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            return context.getBean(AgentDigestApplication.class).exitCode;
        } catch (Exception e) {
            System.err.println("Application failed; check configuration and dependencies.");
            return 1;
        }
    }

    @Bean
    ApplicationRunner digestRunner(DigestProperties config) {
        return arguments -> {
            log.info("Digest configuration: mode={} recoveryOnly={} feeds={} window={} maxCandidates={} maxItems={}",
                    config.offline() ? "OFFLINE" : "LIVE", config.recoveryOnly(), config.feeds() == null ? 0 : config.feeds().size(),
                    config.window(), config.maxCandidates(), config.maxItems());
            Clock clock = Clock.systemUTC();
            var mongoSettings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(config.mongoUri()))
                    .applyToClusterSettings(builder -> builder.serverSelectionTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS))
                    .applyToSocketSettings(builder -> builder.connectTimeout((int) config.timeout().toMillis(), TimeUnit.MILLISECONDS)
                            .readTimeout((int) config.timeout().toMillis(), TimeUnit.MILLISECONDS))
                    .build();
            try (var mongo = MongoClients.create(mongoSettings);
                 var http = new HttpTransport(config.timeout(), config.maxResponseBytes())) {
                var store = new MongoDigestStore(mongo.getDatabase(config.database()), clock);
                var validator = new AnalysisValidator();
                BatchProcessor.Source source;
                BatchProcessor.Analyzer analyzer;
                BatchProcessor.Sender sender;
                if (config.offline()) {
                    source = () -> OfflineFixtures.candidates(clock);
                    analyzer = candidates -> validator.validate(OfflineFixtures.analysis(), candidates,
                            config.maxItems(), clock.instant());
                    sender = OfflineFixtures::send;
                } else {
                    var claude = new ClaudeAnalyzer(http, config);
                    source = new FeedSource(http, config, clock)::retrieve;
                    analyzer = candidates -> {
                        var bounded = claude.boundContext(candidates);
                        if (bounded.isEmpty()) return java.util.List.of();
                        return validator.validate(claude.analyze(bounded), bounded, config.maxItems(), clock.instant());
                    };
                    sender = new TelegramSender(http, config, clock,
                            duration -> Thread.sleep(duration.toMillis()))::send;
                }
                var processor = new BatchProcessor(store, source, analyzer, sender, config.recoveryOnly(), config.maxItems());
                News.Result result = processor.run();
                exitCode = result.exitCode();
                System.out.printf("Digest status=%s delivered=%d unresolved=%d exit=%d%n",
                        result.status(), result.delivered(), result.unresolved(), result.exitCode());
            }
        };
    }
}
