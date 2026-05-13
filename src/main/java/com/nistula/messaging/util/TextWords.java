package com.nistula.messaging.util;

public final class TextWords {

    private TextWords() {}

    public static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
