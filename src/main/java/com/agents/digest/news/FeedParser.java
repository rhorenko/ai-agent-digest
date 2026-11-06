package com.agents.digest.news;

import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Parses feed metadata only; it does not retrieve article bodies. */
public final class FeedParser {
    public List<Candidate> parse(InputStream input, String source, Instant discovered, Instant earliest, int limit) throws Exception {
        if (source == null || source.isBlank() || discovered == null || earliest == null || limit < 1) throw new IllegalArgumentException("Invalid feed parameters");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(input);
        String root = document.getDocumentElement().getLocalName();
        boolean atom = "feed".equals(root);
        if (!atom && !"rss".equals(root)) throw new IllegalArgumentException("Unsupported feed format");
        NodeList entries = document.getElementsByTagNameNS("*", atom ? "entry" : "item");
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < entries.getLength() && result.size() < limit; i++) {
            Element entry = (Element) entries.item(i);
            String title = childText(entry, "title");
            String link = atom ? atomLink(entry) : childText(entry, "link");
            String date = atom ? childText(entry, "published") : childText(entry, "pubDate");
            Instant published = parseDate(date);
            if (title == null || title.isBlank() || link == null || published == null || published.isBefore(earliest) || published.isAfter(discovered)) continue;
            try {
                String url = NewsUrl.normalize(link.trim());
                String excerpt = atom ? childText(entry, "summary") : childText(entry, "description");
                result.add(new Candidate(url, title.trim(), source, url, published, discovered, excerpt == null ? "" : excerpt.trim()));
            } catch (IllegalArgumentException ignored) {
                // Invalid feed URLs are ineligible, not model-supplied substitutes.
            }
        }
        return List.copyOf(result);
    }

    private static String childText(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getLocalName())) return element.getTextContent();
        }
        return null;
    }

    private static String atomLink(Element entry) {
        for (Node child = entry.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "link".equals(element.getLocalName()) &&
                    (element.getAttribute("rel").isEmpty() || "alternate".equals(element.getAttribute("rel")))) return element.getAttribute("href");
        }
        return null;
    }

    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value.trim()); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(value.trim()).toInstant(); } catch (DateTimeParseException ignored) { }
        try { return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); } catch (DateTimeParseException ignored) { return null; }
    }
}
