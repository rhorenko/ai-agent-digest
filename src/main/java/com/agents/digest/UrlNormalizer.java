package com.agents.digest;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class UrlNormalizer {
    private UrlNormalizer() {}

    public static String normalize(String value) {
        URI uri = URI.create(value.trim()).normalize();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null
                || uri.getRawUserInfo() != null || value.length() > 1200) {
            throw new IllegalArgumentException("Invalid article URL");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        String authority = host + ((port == -1 || scheme.equals("https") && port == 443
                || scheme.equals("http") && port == 80) ? "" : ":" + port);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = uri.getRawQuery();
        if (query != null) {
            query = Arrays.stream(query.split("&"))
                    .filter(part -> !part.toLowerCase(Locale.ROOT).matches("(utm_[^=]*|fbclid|gclid)=.*"))
                    .sorted().collect(Collectors.joining("&"));
        }
        return scheme + "://" + authority + path + (query == null || query.isEmpty() ? "" : "?" + query);
    }
}
