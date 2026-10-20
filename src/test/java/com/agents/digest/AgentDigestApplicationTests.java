package com.agents.digest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "spring.main.web-application-type=none")
class AgentDigestApplicationTests {

    @Test
    void runsDemonstrationOperation(CapturedOutput output) {
        assertTrue(output.getOut().contains(AgentDigestApplication.SUCCESS_MESSAGE));
    }
}
