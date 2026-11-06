package com.agents.digest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.DefaultResourceLoader;

final class Resources {
    private Resources() { }
    static String read(String location, int maxBytes) throws Exception {
        try (InputStream stream = new DefaultResourceLoader().getResource(location).getInputStream()) {
            byte[] data = stream.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) throw new IllegalArgumentException("Resource exceeds size limit: " + location);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
