package com.agents.digest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentDigestApplicationTests {
    @Test void missingLiveCredentialsTerminateWithFailure() {
        // Real application lifecycle: configuration fails before any network call.
        assertEquals(1, AgentDigestApplication.execute("--digest.offline=false",
                "--digest.telegram.bot-token=", "--digest.telegram.chat-id=", "--digest.claude.api-key=",
                "--logging.level.root=OFF"));
    }

    @Test void configurationToStringDoesNotExposeCredentials() {
        var config = TestConfig.config(java.net.URI.create("http://localhost"));
        assertFalse(config.toString().contains("fixture-key"));
        assertFalse(config.toString().contains("fixture-token"));
    }
}
