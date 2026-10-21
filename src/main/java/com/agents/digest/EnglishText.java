package com.agents.digest;

final class EnglishText {
    private EnglishText() {}

    static void require(String value) {
        // Script check catches unsupported output; the prompt enforces English semantics.
        boolean hasLatinLetter = value.codePoints().anyMatch(c ->
                Character.isLetter(c) && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN);
        boolean hasOtherScript = value.codePoints().anyMatch(c ->
                Character.isLetter(c) && Character.UnicodeScript.of(c) != Character.UnicodeScript.LATIN);
        if (!hasLatinLetter || hasOtherScript) {
            throw new IllegalArgumentException("ENGLISH_TEXT_REQUIRED");
        }
    }
}
