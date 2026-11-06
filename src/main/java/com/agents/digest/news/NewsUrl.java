package com.agents.digest.news;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Canonicalizes the parts of a URL whose equivalence is defined without site-specific assumptions. */
public final class NewsUrl {
    private NewsUrl() { }

    public static String normalize(String value) {
        try {
            URI uri = new URI(value).normalize();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null ||
                    !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("News URL must be an absolute HTTP(S) URL without user information");
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) {
                port = -1;
            }
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            // Keep raw query and path: decoding or dropping parameters can change article identity.
            String authority = host.contains(":") ? "[" + host + "]" : host;
            if (port != -1) authority += ":" + port;
            return scheme + "://" + authority + path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid news URL", e);
        }
    }
}
