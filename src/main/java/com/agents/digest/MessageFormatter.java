package com.agents.digest;

import com.agents.digest.News.Item;

public final class MessageFormatter {
    private MessageFormatter() {}

    public static String format(Item item) {
        var c = item.candidate();
        var a = item.analysis();
        // Recheck stored items too: recovery can load analysis from older app versions.
        EnglishText.require(c.title());
        EnglishText.require(a.summary());
        EnglishText.require(a.relevance());
        EnglishText.require(a.takeaway());
        EnglishText.require(a.scoreReason());
        String limitation = "HEADLINE_ONLY".equals(c.material())
                ? "Only the headline is available; the full text was not analyzed."
                : "Analysis uses an RSS/Atom excerpt; the full text was not analyzed.";
        String text = c.title() + " — " + a.score() + "/10\n\n"
                + a.summary() + "\n\nWhy it matters: " + a.relevance()
                + "\n\nSuggested action: " + a.takeaway()
                + "\n\nScore reason: " + a.scoreReason()
                + "\n\n" + limitation
                + "\nSource: " + sourceName(c.source()) + "\n" + c.url();
        // Validator bounds each field; preserve the complete URL rather than splitting one item.
        if (text.length() > 4096) throw new IllegalArgumentException("TELEGRAM_MESSAGE_TOO_LONG");
        return text;
    }

    private static String sourceName(String source) {
        return java.net.URI.create(source).getHost();
    }
}
