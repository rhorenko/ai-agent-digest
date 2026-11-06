package com.agents.digest;

import com.agents.digest.news.Candidate;
import com.agents.digest.news.FeedParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class FeedSource {
    private static final int MAX_BYTES = 1_000_000;
    private final DigestProperties config;
    private final HttpClient http;
    public FeedSource(DigestProperties config, HttpClient http) { this.config = config; this.http = http; }

    public List<Candidate> fetch(Instant now) throws Exception {
        var candidates = new LinkedHashMap<String, Candidate>();
        if (config.offline()) {
            String xml = Resources.read("classpath:demo/feed.xml", MAX_BYTES).replace("${PUBLISHED}", DateTimeFormatter.RFC_1123_DATE_TIME.format(now.minusSeconds(3600).atZone(ZoneOffset.UTC)));
            add(candidates, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "Offline fixture", now);
        } else {
            for (String feed : config.feeds()) {
                if (candidates.size() >= config.maxCandidates()) break;
                URI uri = URI.create(feed);
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(config.timeoutSeconds())).header("User-Agent", "AgentDigest/1.0").GET().build();
                HttpResponse<byte[]> response = HttpTransport.send(http, request, MAX_BYTES, config.timeoutSeconds());
                if (response.statusCode() != 200) throw new IllegalStateException("Feed request failed: " + uri.getHost() + " HTTP " + response.statusCode());
                add(candidates, new ByteArrayInputStream(response.body()), uri.getHost(), now);
            }
        }
        return List.copyOf(candidates.values());
    }

    private boolean reachable(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
        for (var address : java.net.InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        }
        // Do not follow redirects from untrusted feed content to internal addresses.
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(config.timeoutSeconds())).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        try (HttpClient verifier = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds())).followRedirects(HttpClient.Redirect.NEVER).build()) {
            int status = verifier.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (java.io.IOException e) { return false; }
    }

    private void add(LinkedHashMap<String, Candidate> out, InputStream body, String source, Instant now) throws Exception {
        var parsed = new FeedParser().parse(body, source, now, now.minus(Duration.ofHours(config.windowHours())), config.maxCandidates());
        for (Candidate item : parsed) {
            if (out.size() == config.maxCandidates()) break;
            if (item.url().length() > 1800 || item.title().length() > 500) continue;
            String excerpt = item.excerpt().replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            if (excerpt.length() > 2000) excerpt = excerpt.substring(0, 2000);
            if (!config.offline() && !reachable(item.url())) continue;
            out.putIfAbsent(item.url(), new Candidate(item.id(), item.title(), item.source(), item.url(), item.publishedAt(), item.discoveredAt(), excerpt));
        }
    }
}



