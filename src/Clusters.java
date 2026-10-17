package com.textanimation;

import android.text.Spanned;
import android.text.style.ReplacementSpan;

import java.text.BreakIterator;

final class Clusters {
    private final BreakIterator iterator = BreakIterator.getCharacterInstance();
    private String text = "";

    void reset(String value) {
        text = value;
        iterator.setText(value);
    }

    int floor(int offset) {
        if (offset <= 0) {
            return 0;
        }
        if (offset >= text.length()) {
            return text.length();
        }
        if (iterator.isBoundary(offset)) {
            return offset;
        }
        int previous = iterator.preceding(offset);
        return previous == BreakIterator.DONE ? offset : previous;
    }

    int ceil(int offset) {
        if (offset <= 0) {
            return 0;
        }
        if (offset >= text.length()) {
            return text.length();
        }
        if (iterator.isBoundary(offset)) {
            return offset;
        }
        int next = iterator.following(offset);
        return next == BreakIterator.DONE ? offset : next;
    }

    int next(int offset, int limit) {
        int next = iterator.following(offset);
        return next == BreakIterator.DONE || next > limit ? limit : next;
    }

    static boolean blank(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return true;
    }

    static boolean emoji(CharSequence text, int start, int end) {
        if (text instanceof Spanned && ((Spanned) text).getSpans(start, end, ReplacementSpan.class).length > 0) {
            return true;
        }
        for (int i = start; i < end; ) {
            int cp = Character.codePointAt(text, i);
            if ((cp >= 0x1F000 && cp <= 0x1FAFF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0x2B00 && cp <= 0x2BFF)
                || (cp >= 0xE0020 && cp <= 0xE007F)
                || cp == 0x200D || cp == 0xFE0F || cp == 0x20E3) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}
