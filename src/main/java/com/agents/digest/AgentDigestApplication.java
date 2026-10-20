package com.agents.digest;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AgentDigestApplication {

    static final String SUCCESS_MESSAGE = "Application completed successfully.";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AgentDigestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext ignored = application.run(args)) {
            // Closing the context makes this template a one-shot application.
        }
    }

    @Bean
    ApplicationRunner demonstrationRunner() {
        return arguments -> System.out.println(SUCCESS_MESSAGE);
    }
}
