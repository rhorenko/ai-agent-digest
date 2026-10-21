package com.agents.digest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agents.digest.News.Candidate;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class FeedSource {
    private static final Logger log = LoggerFactory.getLogger(FeedSource.class);
    private final HttpTransport http;
    private final DigestProperties config;
    private final Clock clock;

    public FeedSource(HttpTransport http, DigestProperties config, Clock clock) {
        this.http = http;
        this.config = config;
        this.clock = clock;
    }

    public List<Candidate> retrieve() throws Exception {
        List<Candidate> candidates = new ArrayList<>();
        List<Iterator<Candidate>> queues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int checkedLinks = 0;
        log.info("Feed retrieval started: feeds={} window={} linkCheckLimit={}",
                config.feeds().size(), config.window(), config.maxCandidates());
        int feedNumber = 0;
        for (URI feed : config.feeds()) {
            long start = System.nanoTime();
            log.info("Feed fetch started: feed={}/{} host={}", ++feedNumber, config.feeds().size(), feed.getHost());
            try {
                var response = http.send(HttpRequest.newBuilder(feed).timeout(config.timeout()).GET().build());
                log.info("Feed HTTP response: feed={} status={}", feedNumber, response.statusCode());
                if (response.statusCode() != 200) throw new IllegalStateException("Feed HTTP failure");
                var parsed = parse(new String(response.body(), StandardCharsets.UTF_8), feed.toString(),
                        clock.instant(), config.window(), config.excerptChars());
                queues.add(parsed.iterator());
                log.info("Feed parsed: feed={} eligibleEntries={}", feedNumber, parsed.size());
                log.info("Feed fetch completed: feed={} elapsedMs={}", feedNumber, (System.nanoTime() - start) / 1_000_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                log.warn("Feed unavailable: feed={} host={} errorType={} elapsedMs={}", feedNumber,
                        feed.getHost(), e.getClass().getSimpleName(), (System.nanoTime() - start) / 1_000_000);
            }
        }
        if (queues.isEmpty()) throw new IllegalStateException("ALL_SOURCES_FAILED");
        log.info("Original link verification started: order=ROUND_ROBIN limit={}", config.maxCandidates());
        // One entry per feed per round preserves source diversity within the global budget.
        boolean remaining = true;
        while (remaining && checkedLinks < config.maxCandidates()) {
            remaining = false;
            for (Iterator<Candidate> queue : queues) {
                if (checkedLinks >= config.maxCandidates()) break;
                if (!queue.hasNext()) continue;
                remaining = true;
                Candidate candidate = queue.next();
                if (!seen.add(candidate.url())) continue;
                checkedLinks++;
                try {
                    if (verifyLink(candidate.url())) candidates.add(candidate);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    log.warn("Original link unavailable: host={} errorType={}",
                            URI.create(candidate.url()).getHost(), e.getClass().getSimpleName());
                }
            }
        }
        log.info("Feed retrieval completed: successfulFeeds={} failedFeeds={} checkedLinks={} candidates={}",
                queues.size(), config.feeds().size() - queues.size(), checkedLinks, candidates.size());
        if (checkedLinks >= config.maxCandidates()) log.info("Feed link-check limit reached: limit={}", config.maxCandidates());
        return candidates;
    }

    private boolean verifyLink(String url) throws Exception {
        URI original = URI.create(url);
        var response = http.send(HttpRequest.newBuilder(original).timeout(config.timeout())
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build());
        int status = response.statusCode();
        log.info("Original link checked: host={} status={}", original.getHost(), status);
        if (status >= 200 && status < 300) return true;
        if ((status == 301 || status == 308) && !original.getRawPath().endsWith("/")) {
            String location = response.headers().firstValue("location").orElse("");
            int query = url.indexOf('?');
            String canonical = query < 0 ? url + "/" : url.substring(0, query) + "/" + url.substring(query);
            // Only a trailing slash may change. No host, scheme, query or credential changes.
            if (!location.isBlank() && canonical.equals(original.resolve(location).toString())) {
                var verified = http.send(HttpRequest.newBuilder(URI.create(canonical)).timeout(config.timeout())
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build());
                log.info("Canonical trailing-slash link checked: host={} status={}", original.getHost(), verified.statusCode());
                return verified.statusCode() >= 200 && verified.statusCode() < 300;
            }
        }
        log.info("Original link rejected: host={} reason=HTTP_STATUS_OR_UNSUPPORTED_REDIRECT status={}", original.getHost(), status);
        return false;
    }

    static List<Candidate> parse(String xml, String source, Instant now, Duration window, int excerptChars)
            throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        var document = builder.parse(new InputSource(new StringReader(xml)));
        NodeList entries = document.getElementsByTagNameNS("*", "item");
        if (entries.getLength() == 0) entries = document.getElementsByTagNameNS("*", "entry");
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            try {
                String title = clean(text(entry, "title"), 300);
                String date = text(entry, "pubDate");
                if (date.isBlank()) date = text(entry, "published");
                // Atom updated is not a publication timestamp; never substitute it.
                Instant published = parseDate(date);
                if (title.isBlank() || published.isAfter(now) || published.isBefore(now.minus(window))) continue;
                String link = "";
                for (Node node = entry.getFirstChild(); node != null; node = node.getNextSibling()) {
                    if (node instanceof Element element && "link".equals(localName(element))) {
                        String rel = element.getAttribute("rel");
                        if (rel.isEmpty() || rel.equals("alternate")) {
                            link = element.hasAttribute("href") ? element.getAttribute("href") : element.getTextContent();
                            break;
                        }
                    }
                }
                String url = UrlNormalizer.normalize(URI.create(source).resolve(link).toString());
                if (link.isBlank()) continue;
                String excerpt = text(entry, "description");
                if (excerpt.isBlank()) excerpt = text(entry, "summary");
                String material = excerpt.isBlank() ? "HEADLINE_ONLY" : "FEED_EXCERPT";
                result.add(new Candidate(UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)).toString(),
                        title, source, url, published, now, clean(excerpt, excerptChars), material));
            } catch (IllegalArgumentException | DateTimeException ignored) {
                // Invalid or undated entries cannot qualify for a recent-news digest.
            }
        }
        result.sort(Comparator.comparing(Candidate::publishedAt).reversed());
        return result;
    }

    private static Instant parseDate(String text) {
        try { return Instant.parse(text.trim()); }
        catch (DateTimeException e) {
            return ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        }
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static String text(Element entry, String name) {
        for (Node node = entry.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) return element.getTextContent();
        }
        return "";
    }

    private static String clean(String value, int limit) {
        String plain = value.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return plain.substring(0, Math.min(limit, plain.length()));
    }
}
